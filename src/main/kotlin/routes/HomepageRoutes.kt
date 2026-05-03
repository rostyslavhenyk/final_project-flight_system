package routes

import data.flight.FlightSearchRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import utils.isStaff
import utils.jsMode
import utils.timed
import java.time.LocalDate

fun Route.homepageRoutes() {
    get("/") { call.handleLoadPage() }
    get("/api/nearest-airport") { call.handleNearestAirport() }
    get("/api/latest-offers") { call.handleLatestOffers() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    timed("T0_homepage_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }

        val originCode = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
        val departDate = request.queryParameters.parseDepartDate()
        val originLabel = FlightSearchRepository.cityForCode(originCode)

        val model =
            mapOf(
                "title" to "Homepage",
                "airports" to FlightSearchRepository.airportLabels(),
                "offerCards" to offerCardsFor(originCode, departDate),
                "originCode" to originCode,
                "originName" to "$originLabel ($originCode)",
                "originLabel" to originLabel,
            )

        renderTemplate("homepage/index.peb", model)
    }
}

private suspend fun ApplicationCall.handleNearestAirport() {
    val firstLabel = FlightSearchRepository.airportLabels().firstOrNull() ?: "Manchester (MAN)"
    val code = FlightSearchRepository.resolveAirportCode(firstLabel) ?: "MAN"
    val name = FlightSearchRepository.cityForCode(code)
    respondText(
        """{"code":"${escapeJson(code)}","name":"${escapeJson("$name ($code)")}"}""",
        ContentType.Application.Json,
    )
}

private suspend fun ApplicationCall.handleLatestOffers() {
    val origin = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
    val departDate = request.queryParameters.parseDepartDate()
    val originLabel =
        request.queryParameters["originLabel"]?.takeIf { it.isNotBlank() } ?: FlightSearchRepository.cityForCode(origin)
    val cards =
        offerCardsFor(origin, departDate).joinToString(",") { card ->
            val images = card.imageUrls.joinToString(",") { "\"${escapeJson(it)}\"" }
            """{"destinationKey":"${escapeJson(
                card.destinationKey,
            )}","destinationName":"${escapeJson(
                card.destinationName,
            )}","bookAirport":"${escapeJson(card.bookAirport)}","priceGbp":${card.priceGbp},"imageUrls":[$images]}"""
        }
    respondText(
        """{"originCode":"${escapeJson(origin)}","originLabel":"${escapeJson(originLabel)}","cards":[$cards]}""",
        ContentType.Application.Json,
    )
}

private data class OfferCard(
    val destinationKey: String,
    val destinationName: String,
    val bookAirport: String,
    val priceGbp: Int,
    val imageUrls: List<String>,
)

private fun offerCardsFor(
    originCode: String,
    departDate: LocalDate,
): List<OfferCard> =
    FlightSearchRepository
        .latestOfferDestinations(originCode, departDate)
        .map { offer ->
            OfferCard(
                destinationKey =
                    "${offer.destinationCity}-${offer.destinationCode}"
                        .lowercase()
                        .replace(
                            Regex("[^a-z0-9]+"),
                            "-",
                        ).trim('-'),
                destinationName = offer.destinationCity,
                bookAirport = "${offer.destinationCity} (${offer.destinationCode})",
                priceGbp = offer.lowestPrice.toInt(),
                imageUrls = destinationImageUrls(offer.destinationCode),
            )
        }

private const val DEFAULT_OFFER_DEPARTURE_OFFSET_DAYS = 14

private fun Parameters.parseDepartDate(): LocalDate =
    this["depart"]
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now().plusDays(DEFAULT_OFFER_DEPARTURE_OFFSET_DAYS.toLong())

private fun destinationImageUrls(destinationCode: String): List<String> =
    destinationImagesByCode[destinationCode.uppercase()] ?: fallbackDestinationImages

private val destinationImagesByCode =
    mapOf(
        "AMS" to
            listOf(
                "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?w=960&q=80",
                "https://images.unsplash.com/photo-1534351590666-13e3e96b5017?w=960&q=80",
            ),
        "BCN" to
            listOf(
                "https://images.unsplash.com/photo-1583422409516-2895a77efded?w=960&q=80",
                "https://images.unsplash.com/photo-1523531294919-4bcd7c65e216?w=960&q=80",
            ),
        "CDG" to
            listOf(
                "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=960&q=80",
                "https://images.unsplash.com/photo-1499856871958-5b9627545d1a?w=960&q=80",
            ),
        "DUB" to
            listOf(
                "https://images.unsplash.com/photo-1549918864-48ac978761a4?w=960&q=80",
                "https://images.unsplash.com/photo-1569263979104-865ab7cd8d13?w=960&q=80",
            ),
        "DXB" to
            listOf(
                "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?w=960&q=80",
                "https://images.unsplash.com/photo-1518684079-3c830dcef090?w=960&q=80",
            ),
        "EDI" to
            listOf(
                "https://images.unsplash.com/photo-1575470522418-b88b692b8084?w=960&q=80",
                "https://images.unsplash.com/photo-1506377585622-bedcbb027afc?w=960&q=80",
            ),
        "FCO" to
            listOf(
                "https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=960&q=80",
                "https://images.unsplash.com/photo-1529260830199-42c24126f198?w=960&q=80",
            ),
        "FRA" to
            listOf(
                "https://images.unsplash.com/photo-1574906292564-37d7e592cb47?w=960&q=80",
                "https://images.unsplash.com/photo-1599946347371-68eb71b16afc?w=960&q=80",
            ),
        "HKG" to
            listOf(
                "https://images.unsplash.com/photo-1536599018102-9f803c140fc1?w=960&q=80",
                "https://images.unsplash.com/photo-1506970845246-18f21d533b20?w=960&q=80",
            ),
        "JFK" to
            listOf(
                "https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=960&q=80",
                "https://images.unsplash.com/photo-1538970272646-f61fabb3a8a2?w=960&q=80",
            ),
        "LHR" to
            listOf(
                "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=960&q=80",
                "https://images.unsplash.com/photo-1486299267070-83823f5448dd?w=960&q=80",
            ),
        "LIS" to
            listOf(
                "https://images.unsplash.com/photo-1500990702037-7620ccb6a60a?w=960&q=80",
                "https://images.unsplash.com/photo-1555881400-74d7acaacd8b?w=960&q=80",
            ),
        "MAD" to
            listOf(
                "https://images.unsplash.com/photo-1539037116277-4db20889f2d4?w=960&q=80",
                "https://images.unsplash.com/photo-1543783207-ec64e4d95325?w=960&q=80",
            ),
        "MAN" to
            listOf(
                "https://images.unsplash.com/photo-1515586838455-8f8f940d6853?w=960&q=80",
                "https://images.unsplash.com/photo-1605648916361-9bc12ad6a569?w=960&q=80",
            ),
        "NCE" to
            listOf(
                "https://images.unsplash.com/photo-1533614767277-878f1b53f9f7?w=960&q=80",
                "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=960&q=80",
            ),
    )

private val fallbackDestinationImages =
    listOf("https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=960&q=80")

private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
