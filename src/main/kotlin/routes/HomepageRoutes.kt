package routes

import data.flight.FlightScheduleRules
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
import java.util.Locale
import kotlin.random.Random

fun Route.homepageRoutes() {
    get("/") { call.handleLoadPage() }
    get("/check-in") { call.handleCheckInPage() }
    get("/offers/free-cancellation") { call.handleFreeCancellationPage() }
    get("/offers/student-fare") { call.handleStudentFarePage() }
    get("/api/latest-offers") { call.handleLatestOffers() }
    get("/api/homepage-cabin-constraints") { call.handleHomepageCabinConstraints() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    timed("T0_homepage_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }

        val originCode = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
        val departDate = request.queryParameters.parseDepartDate()
        val airportsSnapshot = FlightSearchRepository.homepageAirportsSnapshot()
        val originLabel = airportsSnapshot.cityForCode(originCode)

        val model =
            mapOf(
                "title" to "Homepage",
                "airports" to airportsSnapshot.dropdownLabels,
                "offerCards" to offerCardsFor(originCode, departDate, airportsSnapshot),
                "originCode" to originCode,
                "originName" to "$originLabel ($originCode)",
                "originLabel" to originLabel,
            )

        renderTemplate("user/homepage/index.peb", model)
    }
}

private suspend fun ApplicationCall.handleHomepageCabinConstraints() {
    val from = request.queryParameters["from"].orEmpty()
    val to = request.queryParameters["to"].orEmpty()
    val origin = FlightSearchRepository.resolveAirportCode(from)
    val dest = FlightSearchRepository.resolveAirportCode(to)
    val businessSelectable =
        when {
            origin == null || dest == null -> true
            else -> !FlightScheduleRules.isIntraRegionalBusinessRestrictedPair(origin, dest)
        }
    respondText("""{"businessSelectable":$businessSelectable}""", ContentType.Application.Json)
}

private suspend fun ApplicationCall.handleCheckInPage() {
    timed("T0_checkin_page_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }
        renderTemplate(
            "user/check-in/index.peb",
            mapOf(
                "title" to "Check in",
            ),
        )
    }
}

private suspend fun ApplicationCall.handleFreeCancellationPage() {
    timed("T0_offer_free_cancel_page_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }
        renderTemplate(
            "user/offers/free-cancellation/index.peb",
            mapOf("title" to "24-hour free cancellation"),
        )
    }
}

private suspend fun ApplicationCall.handleStudentFarePage() {
    timed("T0_offer_student_fare_page_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }
        renderTemplate(
            "user/offers/student-fare/index.peb",
            mapOf("title" to "Student Fare"),
        )
    }
}

private suspend fun ApplicationCall.handleLatestOffers() {
    val origin = request.queryParameters["origin"]?.takeIf { it.isNotBlank() } ?: "MAN"
    val departDate = request.queryParameters.parseDepartDate()
    val airportsSnapshot = FlightSearchRepository.homepageAirportsSnapshot()
    val originLabel =
        request.queryParameters["originLabel"]?.takeIf { it.isNotBlank() } ?: airportsSnapshot.cityForCode(origin)
    val cards =
        offerCardsFor(origin, departDate, airportsSnapshot).joinToString(",") { card ->
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
) {
    val imageUrlsJoined: String get() = imageUrls.joinToString("|||GLIDE|||")
}

private fun offerCardsFor(
    originCode: String,
    departDate: LocalDate,
    airports: FlightSearchRepository.HomepageAirportsSnapshot,
): List<OfferCard> {
    val origin = originCode.uppercase(Locale.UK)

    val cheapestLightByDestination =
        FlightSearchRepository.lowestLightFaresByDestinationForOrigin(originCode, departDate)

    val usedEffectiveDestinations = mutableSetOf<String>()

    return orderedHomepagePrimarySlots().mapNotNull { slot ->
        var effectiveDest =
            when {
                slot.destCode == origin -> {
                    if (HOMEPAGE_CONFLICT_REPLACEMENT == origin) return@mapNotNull null
                    HOMEPAGE_CONFLICT_REPLACEMENT
                }
                else -> slot.destCode
            }
        if (effectiveDest in usedEffectiveDestinations) {
            effectiveDest =
                HOMEPAGE_DISTINCT_FALLBACK_HUBS.firstOrNull { hub ->
                    hub != origin &&
                        hub !in usedEffectiveDestinations &&
                        cheapestLightByDestination[hub.uppercase(Locale.UK)] != null
                } ?: return@mapNotNull null
        }
        val city = airports.cityForCode(effectiveDest)
        val priceKey = effectiveDest.uppercase(Locale.UK)
        val price =
            cheapestLightByDestination[priceKey]?.toInt()
                ?: return@mapNotNull null
        usedEffectiveDestinations.add(effectiveDest)
        OfferCard(
            destinationKey = destinationSlug(city, effectiveDest),
            destinationName = city,
            bookAirport = "$city ($effectiveDest)",
            priceGbp = price,
            imageUrls = destinationImagesByCode[effectiveDest.uppercase()] ?: fallbackDestinationImages,
        )
    }
}

private const val HOMEPAGE_OFFER_ORDER_SEED = 0x4F46465253485546L

private const val HOMEPAGE_CONFLICT_REPLACEMENT = "AMS"

private val HOMEPAGE_DISTINCT_FALLBACK_HUBS =
    listOf("AMS", "AUH", "DXB", "FRA", "MUC", "DEL", "KUL", "DPS", "LHR", "CPH")

private data class HomepagePrimarySlot(
    val destinationKey: String,
    val destinationName: String,
    val bookAirport: String,
    val destCode: String,
)

private val HOMEPAGE_PRIMARY_OFFERS =
    listOf(
        HomepagePrimarySlot("hong_kong", "Hong Kong", "Hong Kong (HKG)", "HKG"),
        HomepagePrimarySlot("bangkok", "Bangkok", "Bangkok (BKK)", "BKK"),
        HomepagePrimarySlot("singapore", "Singapore", "Singapore (SIN)", "SIN"),
        HomepagePrimarySlot("tokyo", "Tokyo", "Tokyo (NRT)", "NRT"),
        HomepagePrimarySlot("dubai", "Dubai", "Dubai (DXB)", "DXB"),
        HomepagePrimarySlot("sydney", "Sydney", "Sydney (SYD)", "SYD"),
        HomepagePrimarySlot("los_angeles", "Los Angeles", "Los Angeles (LAX)", "LAX"),
        HomepagePrimarySlot("new_york", "New York", "New York (JFK)", "JFK"),
        HomepagePrimarySlot("paris", "Paris", "Paris (CDG)", "CDG"),
        HomepagePrimarySlot("barcelona", "Barcelona", "Barcelona (BCN)", "BCN"),
        HomepagePrimarySlot("vancouver", "Vancouver", "Vancouver (YVR)", "YVR"),
        HomepagePrimarySlot("rome", "Rome", "Rome (FCO)", "FCO"),
    )

private fun orderedHomepagePrimarySlots(): List<HomepagePrimarySlot> {
    val destCodes = HOMEPAGE_PRIMARY_OFFERS.map { it.destCode }
    check(destCodes.size == destCodes.toSet().size) {
        "HOMEPAGE_PRIMARY_OFFERS must have unique destCode values (got duplicates: $destCodes)"
    }
    val hk = HOMEPAGE_PRIMARY_OFFERS.first { it.destCode == "HKG" }
    val others = HOMEPAGE_PRIMARY_OFFERS.filter { it.destCode != "HKG" }
    return listOf(hk) + others.shuffled(Random(HOMEPAGE_OFFER_ORDER_SEED))
}

private fun destinationSlug(
    city: String,
    code: String,
): String =
    "$city-$code"
        .lowercase(Locale.UK)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private const val DEFAULT_OFFER_DEPARTURE_OFFSET_DAYS = 14

private fun Parameters.parseDepartDate(): LocalDate =
    this["depart"]
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now().plusDays(DEFAULT_OFFER_DEPARTURE_OFFSET_DAYS.toLong())

private val destinationImagesByCode =
    mapOf(
        "AMS" to
            listOf(
                "https://images.unsplash.com/photo-1512470876302-972faa2aa9a4?w=1200&q=80",
                "https://images.unsplash.com/photo-1534351590666-13e3e96b5017?w=1200&q=80",
                "https://plus.unsplash.com/premium_photo-1661887237533-b38811c27add?w=1200&q=80",
            ),
        "BCN" to
            listOf(
                "https://images.unsplash.com/photo-1583422409516-2895a77efded?w=1200&q=80",
                "https://images.unsplash.com/photo-1539037116277-4db20889f2d4?w=1200&q=80",
                "https://images.unsplash.com/photo-1722545358667-aed031bf982a?w=1200&q=80",
            ),
        "BKK" to
            listOf(
                "https://images.unsplash.com/photo-1704390529135-742324e6b8f1?w=1200&q=80",
                "https://images.unsplash.com/photo-1523731407965-2430cd12f5e4?w=1200&q=80",
                "https://images.unsplash.com/photo-1532079563951-0c8a7dacddb3?w=1200&q=80",
            ),
        "CDG" to
            listOf(
                "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=1200&q=80",
                "https://images.unsplash.com/photo-1565099824688-e93eb20fe622?w=1200&q=80",
                "https://images.unsplash.com/photo-1507666664345-c49223375e33?w=1200&q=80",
            ),
        "DXB" to
            listOf(
                "https://images.unsplash.com/photo-1512453979798-5ea266f8880c?w=1200&q=80",
                "https://images.unsplash.com/photo-1634148551170-d37d021e0cc9?w=1200&q=80",
                "https://images.unsplash.com/photo-1527288012656-13ea8f91bd63?w=1200&q=80",
            ),
        "FCO" to
            listOf(
                "https://images.unsplash.com/photo-1763935841627-2caf65b9736d?w=1200&q=80",
                "https://images.unsplash.com/photo-1525874684015-58379d421a52?w=1200&q=80",
                "https://images.unsplash.com/photo-1531572753322-ad063cecc140?w=1200&q=80",
            ),
        "HKG" to
            listOf(
                "https://images.unsplash.com/photo-1536599018102-9f803c140fc1?w=1200&q=80",
                "https://images.unsplash.com/photo-1619796404374-aff912b43cd2?w=1200&q=80",
                "https://images.unsplash.com/photo-1577871598838-a543ee47cd79?w=1200&q=80",
            ),
        "JFK" to
            listOf(
                "https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=1200&q=80",
                "https://images.unsplash.com/photo-1518391846015-55a9cc003b25?w=1200&q=80",
                "https://images.unsplash.com/photo-1480714378408-67cf0d13bc1b?w=1200&q=80",
            ),
        "LAX" to
            listOf(
                "https://images.unsplash.com/photo-1580655653885-65763b2597d0?w=1200&q=80",
                "https://images.unsplash.com/photo-1444723121867-7a241cacace9?w=1200&q=80",
                "https://images.unsplash.com/photo-1597982087634-9884f03198ce?w=1200&q=80",
            ),
        "NRT" to
            listOf(
                "https://images.unsplash.com/photo-1604928141064-207cea6f571f?w=1200&q=80",
                "https://images.unsplash.com/photo-1549693578-d683be217e58?w=1200&q=80",
                "https://images.unsplash.com/photo-1557409518-691ebcd96038?w=1200&q=80",
            ),
        "SIN" to
            listOf(
                "https://images.unsplash.com/photo-1565967511849-76a60a516170?w=1200&q=80",
                "https://images.unsplash.com/photo-1441805983468-f5a1a9f985fb?w=1200&q=80",
                "https://images.unsplash.com/photo-1555650645-ff8cf7397d9a?w=1200&q=80",
            ),
        "SYD" to
            listOf(
                "https://images.unsplash.com/photo-1506973035872-a4ec16b8e8d9?w=1200&q=80",
                "https://images.unsplash.com/photo-1467803738586-46b7eb7b16a1?w=1200&q=80",
                "https://images.unsplash.com/photo-1624138784614-87fd1b6528f8?w=1200&q=80",
            ),
        "YVR" to
            listOf(
                "https://images.unsplash.com/photo-1559511260-66a654ae982a?w=1200&q=80",
                "https://images.unsplash.com/photo-1606962849307-a6eae721a075?w=1200&q=80",
                "https://images.unsplash.com/photo-1502228362178-086346ac6862?w=1200&q=80",
            ),
    )

private val fallbackDestinationImages =
    listOf("https://images.unsplash.com/photo-1436491865332-7a61a109cc05?w=960&q=80")

private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
