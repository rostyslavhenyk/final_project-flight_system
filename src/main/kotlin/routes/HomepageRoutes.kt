package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import data.AirportRepository
import data.GeoRepository
import data.LatestOffersService
import utils.jsMode
import utils.timed

fun Route.homepageRoutes() {
    // Registers the homepage URL.
    get("/") { call.handleLoadPage() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    // Timed wrapper used by module instrumentation.
    timed("T1_homepage_load", jsMode()) {
        val pebble = getEngine()
        // Optional origin code from query string (e.g. /?origin=LBA).
        val originCode = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
        // Resolve the human-readable origin name from geo dataset.
        val nearest = GeoRepository.allGeo().find { it.code == originCode }
        val originName = nearest?.name ?: "Manchester (MAN)"
        val originLabel = originName.substringBefore(" (")

        // Model passed to homepage template:
        // - airports: for autocomplete dropdowns
        // - offerCards: priced destination cards (distance-based random GBP)
        val model =
            mapOf(
                "title" to "Homepage",
                "airports" to AirportRepository.all(),
                "offerCards" to LatestOffersService.cardsForOrigin(originCode),
                "originCode" to originCode,
                "originName" to originName,
                "originLabel" to originLabel,
            )

        val template = pebble.getTemplate("homepage/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
