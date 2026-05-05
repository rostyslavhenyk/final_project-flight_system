import auth.UserSession
import data.AllTables
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
import routes.*
import routes.configureHealthCheck
import routes.staff.staffRoutes
import utils.DatabaseFactory
import utils.SessionUtils

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = "0.0.0.0"

    embeddedServer(Netty, port = port, host = host) {
        configureDatabase()
        configureLogging()
        configureTemplating()
        configureSessions()
        configureRouting()
    }.start(wait = true)
}

// sets up the database and creates tables
@Suppress("SpreadOperator")
fun Application.configureDatabase() {
    DatabaseFactory.init()

    transaction {
        SchemaUtils.create(*AllTables.all())
    }
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
        flightsRoutes()
        membershipRoutes()
        helpRoutes()
        signUpRoutes()
        logInRoutes()
        myAccountRoutes()
        staffRoutes()
        verificationRoutes()
        chatRoutes()
    }
}
