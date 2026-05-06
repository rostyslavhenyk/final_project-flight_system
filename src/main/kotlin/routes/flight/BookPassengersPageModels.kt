package routes.flight

import auth.LoggedInState
import data.flight.FlightSearchRepository
import io.ktor.http.Parameters
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val MAX_ADULT_PASSENGERS = 9
private const val MAX_CHILD_PASSENGERS = 8
private const val MEMBER_NUMBER_FORMAT = "GA%06d"

internal fun bookPassengersModel(
    queryParams: Parameters,
    currentUri: String,
    logged: LoggedInState,
): Map<String, Any?> {
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    val departRaw = queryParams["depart"].orEmpty()
    val adults = queryParams["adults"]?.toIntOrNull()?.coerceIn(1, MAX_ADULT_PASSENGERS) ?: 1
    val children = queryParams["children"]?.toIntOrNull()?.coerceIn(0, MAX_CHILD_PASSENGERS) ?: 0
    val session = logged.session
    val segment = passengerSegmentSnapshot(queryParams)
    val cabinEff = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
    return mapOf(
        "title" to "Passenger details",
        "fromRaw" to fromRaw,
        "toRaw" to toRaw,
        "departRaw" to departRaw,
        "returnRaw" to queryParams["return"].orEmpty(),
        "trip" to queryParams["trip"].orEmpty(),
        "cabinClass" to cabinEff,
        "adults" to adults.toString(),
        "children" to children.toString(),
        "fare" to queryParams["fare"].orEmpty(),
        "flight" to queryParams["flight"].orEmpty(),
        "price" to queryParams["price"].orEmpty(),
        "backToChooseFlightsHref" to backToChooseFlightsHref(queryParams, fromRaw, toRaw, departRaw),
        "passengerRows" to buildPassengerRowModels(adults, children),
        "hasFlightDetail" to segment.hasFlightDetail,
        "segDep" to segment.departure,
        "segArr" to segment.arrival,
        "segDur" to segment.duration,
        "segFlights" to segment.flights,
        "segOrig" to segment.origin,
        "segDest" to segment.destination,
        "segArrPlus" to segment.arrivalPlusDays,
        "loginHref" to "/login?returnUrl=" + URLEncoder.encode(currentUri, StandardCharsets.UTF_8),
        "membershipValue" to
            if (logged.loggedIn && session != null) {
                String.format(Locale.UK, MEMBER_NUMBER_FORMAT, session.id)
            } else {
                ""
            },
        "membershipFilled" to (logged.loggedIn && session != null),
        "continueSeatsHref" to bookingHref("/book/seats", queryParams),
    )
}

private data class PassengerSegmentSnapshot(
    val departure: String,
    val arrival: String,
    val duration: String,
    val flights: String,
    val origin: String,
    val destination: String,
    val arrivalPlusDays: Int,
) {
    val hasFlightDetail: Boolean = departure.isNotBlank() && arrival.isNotBlank()
}

private fun passengerSegmentSnapshot(queryParams: Parameters): PassengerSegmentSnapshot =
    PassengerSegmentSnapshot(
        departure = queryParams["segDep"].orEmpty(),
        arrival = queryParams["segArr"].orEmpty(),
        duration = queryParams["segDur"].orEmpty(),
        flights = queryParams["segFlights"].orEmpty(),
        origin = queryParams["segOrig"].orEmpty(),
        destination = queryParams["segDest"].orEmpty(),
        arrivalPlusDays = queryParams["segArrPlus"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )

private fun backToChooseFlightsHref(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): String {
    val cabinEff = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
    return if (isInboundPassengerLeg(queryParams, fromRaw, toRaw)) {
        inboundPassengerSearchHref(queryParams, fromRaw, toRaw, departRaw)
    } else {
        flightsHref(buildBaseParams(queryParams, fromRaw, toRaw, departRaw, cabinEff) + mapOf("page" to "1"))
    }
}

private fun isInboundPassengerLeg(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
): Boolean {
    val legNorm = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
    val fromCode = FlightSearchRepository.resolveAirportCode(fromRaw)
    val toCode = FlightSearchRepository.resolveAirportCode(toRaw)
    val obFromCode = FlightSearchRepository.resolveAirportCode(queryParams["obFrom"].orEmpty())
    val obToCode = FlightSearchRepository.resolveAirportCode(queryParams["obTo"].orEmpty())
    val inferredInbound =
        legNorm.isBlank() &&
            queryParams["trip"].equals("return", ignoreCase = true) &&
            fromCode != null &&
            toCode != null &&
            obFromCode != null &&
            obToCode != null &&
            fromCode == obToCode &&
            toCode == obFromCode
    return legNorm == "inbound" || inferredInbound
}

private fun inboundPassengerSearchHref(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): String {
    val cabinEff = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
    val returnSearchParams = LinkedHashMap(buildBaseParams(queryParams, fromRaw, toRaw, departRaw, cabinEff))
    returnSearchParams["page"] = "1"
    if (!queryParams["leg"].equals("inbound", ignoreCase = true)) {
        returnSearchParams["leg"] = "inbound"
    }
    return flightsHref(returnSearchParams)
}

/** Per-passenger row: global `slot`, screen-reader `heading`, wireframe `badgeTier`. */
private fun buildPassengerRowModels(
    adults: Int,
    children: Int,
): List<Map<String, Any>> {
    val rows = ArrayList<Map<String, Any>>()
    var slot = 1
    repeat(adults) {
        rows.add(
            mapOf(
                "slot" to slot,
                "heading" to "Adult passenger $slot",
                "kind" to "adult",
                "badgeTier" to "ADULT",
            ),
        )
        slot++
    }
    repeat(children) {
        rows.add(
            mapOf(
                "slot" to slot,
                "heading" to "Child passenger $slot",
                "kind" to "child",
                "badgeTier" to "CHILDREN",
            ),
        )
        slot++
    }
    return rows
}
