import auth.UserSession
import data.AllTables
import data.SeatMaintenance
import data.UserMaintenance
import data.flight.FlightScheduleGenerator
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import routes.commitmentRoutes
import routes.chatRoutes
import routes.flight.flightsRoutes
import routes.helpRoutes
import routes.homepageRoutes
import routes.legalRoutes
import routes.logInRoutes
import routes.membershipRoutes
import routes.myAccountRoutes
import routes.settingsRoutes
import routes.signUpRoutes
import routes.staff.configureHealthCheck
import routes.staff.staffRoutes
import routes.verificationRoutes
import utils.DatabaseFactory
import utils.SessionUtils
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val SCHEDULE_MAINTENANCE_HOUR = 0
private const val SCHEDULE_MAINTENANCE_MINUTE = 5

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = "0.0.0.0"

    embeddedServer(Netty, port = port, host = host) {
        configureDatabase()
        configureLogging()
        configureTemplating()
        configureFlightScheduleMaintenance()
        configureSessions()
        configureRouting()
    }.start(wait = true)
}

fun configureDatabase() {
    DatabaseFactory.init()

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            tables = AllTables.all(),
        )
        SeatMaintenance.ensureUniqueSeatIndex()
        FlightScheduleGenerator.ensureSeedData()
    }
    UserMaintenance.normalizeStoredNames()
}

fun Application.configureFlightScheduleMaintenance() {
    val scheduler =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "flight-schedule-maintenance").apply {
                isDaemon = true
            }
        }
    val initialDelayMillis = millisUntilNextScheduleMaintenance()
    val dailyDelayMillis = TimeUnit.DAYS.toMillis(1)

    scheduler.scheduleWithFixedDelay(
        {
            runCatching {
                transaction {
                    FlightScheduleGenerator.ensureSeedData()
                }
                log.info("Daily flight schedule maintenance completed")
            }.onFailure { error ->
                log.error("Daily flight schedule maintenance failed", error)
            }
        },
        initialDelayMillis,
        dailyDelayMillis,
        TimeUnit.MILLISECONDS,
    )

    environment.monitor.subscribe(ApplicationStopping) {
        scheduler.shutdownNow()
    }
}

private fun millisUntilNextScheduleMaintenance(): Long {
    val now = LocalDateTime.now()
    val nextRun =
        LocalDateTime.of(
            LocalDate.now().plusDays(1),
            LocalTime.of(SCHEDULE_MAINTENANCE_HOUR, SCHEDULE_MAINTENANCE_MINUTE),
        )
    return Duration.between(now, nextRun).toMillis().coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
}

fun Application.configureLogging() {
    install(CallLogging) {
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "$method $path - $status"
        }
    }
}

fun Application.configureTemplating() {
    environment.monitor.subscribe(ApplicationStarted) {
        log.info("Pebble templates loaded from resources/templates/")
        log.info("Server running on configured port")
    }
}

fun ApplicationCall.isHtmxRequest(): Boolean = request.headers["HX-Request"] == "true"

// sets up session cookies
fun Application.configureSessions() {
    install(Sessions) {
        cookie<SessionUtils>("SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Strict"
        }
        cookie<UserSession>("USER_SESSION") {
            cookie.path = "/"
            cookie.httpOnly = true
        }
    }
}

// registers all routes
fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")

        configureHealthCheck()

        homepageRoutes()
        commitmentRoutes()
        legalRoutes()
        flightsRoutes()
        membershipRoutes()
        helpRoutes()
        settingsRoutes()
        signUpRoutes()
        logInRoutes()
        myAccountRoutes()
        staffRoutes()
        verificationRoutes()
        chatRoutes()
    }
}
