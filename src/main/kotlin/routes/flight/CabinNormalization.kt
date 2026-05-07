package routes.flight

import data.flight.FlightScheduleRules
import data.flight.FlightSearchRepository
import io.ktor.http.Parameters
import java.util.Locale

/** Keeps cabin query values aligned with current economy/business rules. */
internal object CabinNormalization {
    fun normalizedCabinForSearch(
        cabinRaw: String,
        originCode: String?,
        destCode: String?,
    ): String = normalizedCabinForLegs(cabinRaw, listOfNotNull(originCode?.let { o -> destCode?.let { d -> o to d } }))

    fun normalizedCabinForLegs(
        cabinRaw: String,
        legs: List<Pair<String, String>>,
    ): String =
        if (
            cabinRaw.lowercase(Locale.UK).trim() == "business" &&
            !legs.any { (origin, dest) -> FlightScheduleRules.isIntraRegionalBusinessRestrictedPair(origin, dest) }
        ) {
            "business"
        } else {
            "economy"
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

    /** Return bookings consider both outbound and inbound route pairs. */
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
