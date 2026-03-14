import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.pebbletemplates.pebble.PebbleEngine
import routes.*
import auth.UserSession
import routes.configureHealthCheck
import utils.SessionData
import java.io.StringWriter
import io.ktor.util.*

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = "0.0.0.0"

    embeddedServer(Netty, port = port, host = host) {
        configureLogging()
        configureTemplating()
        configureSessions()
        configureRouting()
    }.start(wait = true)
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
    val pebbleEngine =
        PebbleEngine
            .Builder()
            .loader(
                io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                    prefix = "templates/"
                },
            ).autoEscaping(true)
            .cacheActive(false)
            .strictVariables(false)
            .build()

    environment.monitor.subscribe(ApplicationStarted) {
        log.info("✓ Pebble templates loaded from resources/templates/")
        log.info("✓ Server running on configured port")
    }

    attributes.put(PebbleEngineKey, pebbleEngine)
}

val PebbleEngineKey = AttributeKey<PebbleEngine>("PebbleEngine")

suspend fun ApplicationCall.renderTemplate(
    templateName: String,
    context: Map<String, Any> = emptyMap(),
): String {
    val engine = application.attributes[PebbleEngineKey]
    val writer = StringWriter()
    val template = engine.getTemplate(templateName)

    val sessionData = sessions.get<SessionData>()
    val enrichedContext =
        context +
            mapOf(
                "sessionId" to (sessionData?.id ?: "anonymous"),
                "isHtmx" to isHtmxRequest(),
            )

    template.evaluate(writer, enrichedContext)
    return writer.toString()
}

fun ApplicationCall.isHtmxRequest(): Boolean = request.headers["HX-Request"] == "true"

fun Application.configureSessions() {
    install(Sessions) {
        cookie<SessionData>("SESSION") {
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

fun Application.configureRouting() {
    routing {
        staticResources("/static", "static")

        configureHealthCheck()

        homepageRoutes()
        commitmentRoutes()
        flightsRoutes()
        membershipRoutes()
        helpRoutes()
        signUpRoutes()
        logInRoutes()
        myAccountRoutes()
    }
}
