package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import java.time.LocalDate
import data.AirportRepository
import data.GeoRepository
import data.LatestOffersService
import utils.jsMode
import utils.timed

fun Route.homepageRoutes() {
    get("/") { call.handleLoadPage() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    // `timed` is for coursework timing hooks (see utils.timed).
    timed("T1_homepage_load", jsMode()) {
        val pebble = getEngine()
        // Optional `?origin=LBA` on the URL overrides the default below.
        val originCode = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
        val departDate =
            request.queryParameters["depart"]
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: LocalDate.now().plusDays(14)
        // Look up a display name from `data/airports_geo.csv` (via GeoRepository).
        val nearest = GeoRepository.allGeo().find { it.code == originCode }
        val originName = nearest?.name ?: "Manchester (MAN)"
        val originLabel = originName.substringBefore(" (")

        // Pebble model keys must match what `homepage/index.peb` reads.
        // `offerCards` prices use the same Economy Light logic as `/search-flights` for the chosen date.
        val model =
            mapOf(
                "title" to "Homepage",
                "airports" to AirportRepository.all(),
                "offerCards" to LatestOffersService.cardsForOrigin(originCode, departDate),
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
