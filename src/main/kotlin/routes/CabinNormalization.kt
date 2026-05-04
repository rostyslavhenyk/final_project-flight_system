package routes

import data.flight.FlightScheduleRules
import data.flight.FlightSearchRepository
import io.ktor.http.Parameters
import java.util.Locale

/**
 * Normalises `cabinClass` from query strings so search, booking hand-offs, and passenger steps agree with product rules
 * (no first class; business downgraded to economy on intra-regional UK/EU pairs via [FlightScheduleRules]).
 *
 * **Used by**
 * - [routes.SearchFlightsPageModels] — effective cabin for search.
 * - [routes.FlightRouteHelpers] — cabin in generated flight URLs.
 * - [routes.FlightBookingHelpers] — preserved/rebuilt `cabinClass` across `/book/...` steps.
 * - [routes.BookPassengersPageModels], [routes.BookReviewPageModels] — `cabinEff` for templates and links.
 *
 * **Grep:** `CABIN-TWEAKS`
 */
internal object CabinNormalization {
    fun normalizedCabinForSearch(
        cabinRaw: String,
        originCode: String?,
        destCode: String?,
    ): String = normalizedCabinForLegs(cabinRaw, listOfNotNull(originCode?.let { o -> destCode?.let { d -> o to d } }))

    fun normalizedCabinForLegs(
        cabinRaw: String,
        legs: List<Pair<String, String>>,
    ): String {
        var c = cabinRaw.lowercase(Locale.UK).trim().ifBlank { "economy" }
        // Codes used to cancel first class feature: treat "first" as economy everywhere.
        if (c == "first") {
            c = "economy"
        } else if (c == "business") {
            // Restrict business on intra-regional UK/EU pairs (see FlightScheduleRules).
            c =
                if (legs.isEmpty()) {
                    "business"
                } else if (
                    legs.any { (origin, dest) ->
                        FlightScheduleRules.isIntraRegionalBusinessRestrictedPair(origin, dest)
                    }
                ) {
                    "economy"
                } else {
                    "business"
                }
        }
        return c
    }

    fun normalizedCabinFromQuery(
        queryParams: Parameters,
        fromRaw: String,
        toRaw: String,
    ): String {
        val cabin = queryParams["cabinClass"].orEmpty()
        val fromCode = FlightSearchRepository.resolveAirportCode(fromRaw)
        val toCode = FlightSearchRepository.resolveAirportCode(toRaw)
        return normalizedCabinForSearch(cabin, fromCode, toCode)
    }

    /**
     * Coerces cabin for `/book/...` hand-offs: return trips consider outbound (`obFrom`/`obTo`) and inbound
     * (`from`/`to`) pairs.
     */
    fun normalizedCabinForBookingQuery(queryParams: Parameters): String {
        val raw = queryParams["cabinClass"].orEmpty()
        val tripReturn = queryParams["trip"].equals("return", ignoreCase = true)
        val obFrom = queryParams["obFrom"].orEmpty()
        val obTo = queryParams["obTo"].orEmpty()
        val from = queryParams["from"].orEmpty()
        val to = queryParams["to"].orEmpty()
        val hasOutboundPair = obFrom.isNotBlank() && obTo.isNotBlank()
        val hasInboundPair = from.isNotBlank() && to.isNotBlank()
        if (tripReturn && hasOutboundPair && hasInboundPair) {
            val legs =
                buildList {
                    val o1 = FlightSearchRepository.resolveAirportCode(obFrom)
                    val o2 = FlightSearchRepository.resolveAirportCode(obTo)
                    if (o1 != null && o2 != null && o1 != o2) add(o1 to o2)
                    val i1 = FlightSearchRepository.resolveAirportCode(from)
                    val i2 = FlightSearchRepository.resolveAirportCode(to)
                    if (i1 != null && i2 != null && i1 != i2) add(i1 to i2)
                }
            if (legs.isNotEmpty()) return normalizedCabinForLegs(raw, legs)
        }
        return normalizedCabinForSearch(
            raw,
            FlightSearchRepository.resolveAirportCode(from),
            FlightSearchRepository.resolveAirportCode(to),
        )
    }
}
