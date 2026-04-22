import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.sessions.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.pebbletemplates.pebble.PebbleEngine
import routes.*
import auth.UserSession
import routes.configureHealthCheck
import utils.SessionData
import data.GeoRepository
import data.LatestOffersService
import java.io.StringWriter
import io.ktor.util.*

fun main() {
    // Read deployment port from environment; fall back to local default.
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
    // Lightweight access log format for route debugging.
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
    // Configure Pebble template engine for server-rendered HTML.
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
    // Configure anonymous and authenticated session cookies.
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
        // Static assets (CSS/JS/images/fonts) served from resources/static.
        staticResources("/static", "static")

        configureHealthCheck()

        // API endpoint used by homepage JS to resolve nearest airport by lat/lon.
        get("/api/nearest-airport") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lon = call.request.queryParameters["lon"]?.toDoubleOrNull()
            val airport =
                if (lat != null && lon != null) GeoRepository.nearestAirport(lat, lon)
                else null
            val json =
                if (airport != null) """{"code":"${airport.code}","name":"${airport.name}"}"""
                else """{"code":"MAN","name":"Manchester (MAN)"}"""
            call.respondText(json, ContentType.Application.Json)
        }

        get("/api/latest-offers") {
            val origin = call.request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
            val originLabelOverride = call.request.queryParameters["originLabel"]?.takeIf { it.isNotBlank() }
            val nearest = GeoRepository.allGeo().find { it.code == origin }
            val originLabel =
                originLabelOverride
                    ?: nearest?.name?.substringBefore(" (")
                    ?: origin

            fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

            val cards = LatestOffersService.cardsForOrigin(origin)
            val cardJson =
                cards.joinToString(",") { c ->
                    val imgs = c.imageUrls.joinToString(",") { "\"${esc(it)}\"" }
                    """{"destinationKey":"${esc(c.destinationKey)}","destinationName":"${esc(c.destinationName)}","bookAirport":"${esc(c.bookAirport)}","priceGbp":${c.priceGbp},"imageUrls":[$imgs]}"""
                }
            val json =
                """{"originCode":"${esc(origin)}","originLabel":"${esc(originLabel)}","cards":[$cardJson]}"""
            call.respondText(json, ContentType.Application.Json)
        }

        homepageRoutes()
        commitmentRoutes()
        legalRoutes()
        flightsRoutes()
        membershipRoutes()
        helpRoutes()
        signUpRoutes()
        logInRoutes()
        myAccountRoutes()
    }
}
