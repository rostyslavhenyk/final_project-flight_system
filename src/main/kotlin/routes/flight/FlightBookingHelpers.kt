package routes.flight

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightScheduleRecord
import data.flight.FlightSearchRepository.FlightSortOption
import data.FlightRepository
import data.flight.FlightRecordMapper
import io.ktor.http.Parameters
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Locale

private const val BOOKING_SEARCH_PAGE_SIZE = 400
private val bookingFlightNumberRegex = Regex("""GA\d{1,6}""")

private val bookingQueryKeys =
    listOf(
        "from",
        "to",
        "depart",
        "trip",
        "return",
        "cabinClass",
        "adults",
        "children",
        "leg",
        "obFrom",
        "obTo",
        "obDepart",
        "outboundPrice",
        "obFlight",
        "obFare",
        "fare",
        "flight",
        "price",
        "segDep",
        "segArr",
        "segDur",
        "segFlights",
        "segArrPlus",
        "segOrig",
        "segDest",
        "seatSel",
        "paxSel",
        "extras",
    )

/** Preserves booking state across /book/... steps. */
internal fun bookingParamsMap(queryParams: Parameters): LinkedHashMap<String, String> {
    val preservedParams = LinkedHashMap<String, String>()
    for (queryKey in bookingQueryKeys) {
        queryParams[queryKey]?.takeIf { it.isNotBlank() }?.let { preservedValue ->
            preservedParams[queryKey] = preservedValue
        }
    }
    if (queryParams["from"].orEmpty().isNotBlank() && queryParams["to"].orEmpty().isNotBlank()) {
        preservedParams["cabinClass"] = CabinNormalization.normalizedCabinForBookingQuery(queryParams)
    }
    return preservedParams
}

internal fun bookingHref(
    path: String,
    queryParams: Parameters,
): String {
    val preservedParams = bookingParamsMap(queryParams)
    if (preservedParams.isEmpty()) return path
    val encode: (String) -> String = { value ->
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val queryString = preservedParams.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
    return "$path?$queryString"
}

/**
 * When `cabinClass` is non-blank but not canonical,
 * returns [path] with a normalized query string from [bookingHref]. Otherwise null (no redirect).
 */
internal fun bookPathRedirectIfCanonicalCabinNeeded(
    path: String,
    queryParams: Parameters,
): String? {
    val raw = queryParams["cabinClass"]?.trim().orEmpty()
    val eff = CabinNormalization.normalizedCabinForBookingQuery(queryParams)
    return bookingHref(path, queryParams)
        .takeIf { raw.isNotBlank() && !raw.equals(eff, ignoreCase = true) }
}

internal fun findRecordForRouteAndFlightId(
    fromRaw: String,
    toRaw: String,
    departRaw: String,
    flightId: String,
): FlightScheduleRecord? {
    val origin = FlightSearchRepository.resolveAirportCode(fromRaw)
    val destination = FlightSearchRepository.resolveAirportCode(toRaw)
    val departureDate = runCatching { LocalDate.parse(departRaw) }.getOrNull()
    val missingRoutePart = origin == null || destination == null || departureDate == null
    return if (flightId.isBlank() || missingRoutePart) {
        null
    } else {
        directFlightRecord(departureDate, flightId)
            ?: searchRouteRecord(origin, destination, departureDate, flightId)
    }
}

internal fun findRecordForBooking(queryParams: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = queryParams["from"].orEmpty(),
        toRaw = queryParams["to"].orEmpty(),
        departRaw = queryParams["depart"].orEmpty(),
        flightId = queryParams["flight"].orEmpty(),
    )

internal fun findOutboundRecordForBooking(queryParams: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = queryParams["obFrom"].orEmpty(),
        toRaw = queryParams["obTo"].orEmpty(),
        departRaw = queryParams["obDepart"].orEmpty(),
        flightId = queryParams["obFlight"].orEmpty(),
    )

private fun searchRouteRecord(
    origin: String,
    destination: String,
    departureDate: LocalDate,
    flightId: String,
): FlightScheduleRecord? =
    FlightSearchRepository
        .search(
            originCode = origin,
            destCode = destination,
            depart = departureDate,
            sort = FlightSortOption.Recommended,
            ascending = true,
            stopFilter = null,
            page = 1,
            pageSize = BOOKING_SEARCH_PAGE_SIZE,
        ).rows
        .find { row ->
            val rowFlightId =
                encAttr(
                    listOf(
                        row.originCode,
                        row.destCode,
                        row.departDate.toString(),
                        row.legFlightNumbers.joinToString("-"),
                        row.departTime.toString(),
                    ).joinToString("-"),
                )
            rowFlightId == flightId
        }

private fun directFlightRecord(
    departureDate: LocalDate,
    flightId: String,
): FlightScheduleRecord? {
    val flightNumbers = bookingFlightNumberRegex.findAll(flightId).map { match -> match.value }.toList()
    val selectedFlightId = flightNumbers.singleOrNull()?.flightIdFromNumber()
    val flight =
        selectedFlightId?.let { id ->
            FlightRepository
                .allFull(firstDateInclusive = departureDate, lastDateInclusive = departureDate)
                .find { full -> full.flight.flightID == id }
        }
    return flight?.let { full -> FlightRecordMapper.recordsForDate(departureDate, listOf(full)).singleOrNull() }
}

/** Human label e.g. `Economy Light`, `Business Flex`. */
internal fun farePackageDisplayName(
    tierRaw: String,
    cabinRaw: String,
): String {
    val tier = tierRaw.lowercase(Locale.UK).trim()
    val cabin = cabinRaw.lowercase(Locale.UK).trim()
    return when (tier) {
        "light" -> cabinFareName(cabin, "Light")
        "essential" -> cabinFareName(cabin, "Essential")
        "flex" -> cabinFareName(cabin, "Flex")
        else -> tierRaw.replaceFirstChar { it.uppercaseChar() }
    }
}

private fun cabinFareName(
    cabin: String,
    tierName: String,
): String =
    when (cabin) {
        "business" -> "Business $tierName"
        else -> "Economy $tierName"
    }

internal fun backToFlightSearchHref(queryParams: Parameters): String {
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    val departRaw = queryParams["depart"].orEmpty()
    return if (isInboundBookingLeg(queryParams, fromRaw, toRaw)) {
        inboundBookingSearchHref(queryParams, fromRaw, toRaw, departRaw)
    } else {
        val cabinEff = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
        flightsHref(buildBaseParams(queryParams, fromRaw, toRaw, departRaw, cabinEff) + mapOf("page" to "1"))
    }
}

private fun isInboundBookingLeg(
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

private fun inboundBookingSearchHref(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): String {
    val cabinEff = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
    val outboundParams = LinkedHashMap(buildBaseParams(queryParams, fromRaw, toRaw, departRaw, cabinEff))
    outboundParams["page"] = "1"
    if (!queryParams["leg"].equals("inbound", ignoreCase = true)) {
        outboundParams["leg"] = "inbound"
    }
    return flightsHref(outboundParams)
}

/** Inbound flight list with the chosen outbound flight + fare preserved. */
internal fun inboundSearchResultsHref(queryParams: Parameters): String {
    val inboundParams = LinkedHashMap<String, String>()
    listOf("from", "to", "depart", "adults", "children").forEach { queryKey ->
        queryParams[queryKey]?.takeIf { it.isNotBlank() }?.let { inboundParams[queryKey] = it }
    }
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    inboundParams["cabinClass"] = CabinNormalization.normalizedCabinFromQuery(queryParams, fromRaw, toRaw)
    inboundParams["trip"] = "return"
    inboundParams["return"] = ""
    inboundParams["leg"] = "inbound"
    inboundParams["page"] = "1"
    listOf("obFrom", "obTo", "obDepart", "obFlight", "obFare", "outboundPrice").forEach { queryKey ->
        queryParams[queryKey]?.takeIf { it.isNotBlank() }?.let { inboundParams[queryKey] = it }
    }
    queryParams["obFlight"]?.takeIf { it.isNotBlank() }?.let { inboundParams["flight"] = it }
    queryParams["obFare"]?.takeIf { it.isNotBlank() }?.let { inboundParams["fare"] = it }
    return flightsHref(inboundParams)
}

internal fun effectiveFareTier(tierRaw: String): String {
    val normalizedTier = tierRaw.lowercase(Locale.UK).trim()
    return if (normalizedTier.isBlank()) "flex" else normalizedTier
}

internal fun moneyForTier(
    fares: Map<String, BigDecimal>,
    tier: String,
): BigDecimal =
    when (tier) {
        "light" -> fares.getValue("light")
        "essential" -> fares.getValue("essential")
        "flex" -> fares.getValue("flex")
        else -> fares.getValue("from")
    }

internal fun routeCityPairLine(
    fromRaw: String,
    toRaw: String,
): String {
    val origin = FlightSearchRepository.resolveAirportCode(fromRaw)
    val destination = FlightSearchRepository.resolveAirportCode(toRaw)
    return if (origin == null || destination == null) {
        ""
    } else {
        "${FlightSearchRepository.cityForCode(origin)} to ${FlightSearchRepository.cityForCode(destination)}"
    }
}
