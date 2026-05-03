package routes

import data.flight.AirportTimeZoneResolver
import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightScheduleRecord
import data.flight.FlightSearchRepository.FlightSortOption
import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUTES_PER_HOUR = 60
private const val DATE_CAROUSEL_VISIBLE_DAYS = 7
private const val DATE_CAROUSEL_CENTER_OFFSET_DAYS = 3

/** Maps `sort` query value to [FlightSortOption]; unknown values fall back to Recommended. */
internal fun parseFlightSortOption(raw: String?): FlightSortOption =
    when (raw?.lowercase(Locale.UK)) {
        "departure" -> FlightSortOption.DepartureTime
        "arrival" -> FlightSortOption.ArrivalTime
        "duration" -> FlightSortOption.Duration
        "fare" -> FlightSortOption.Fare
        "stops" -> FlightSortOption.Stops
        else -> FlightSortOption.Recommended
    }

/** Reverse of [parseFlightSortOption] for building `sort=` links. */
internal fun FlightSortOption.toParam(): String =
    when (this) {
        FlightSortOption.Recommended -> "recommended"
        FlightSortOption.DepartureTime -> "departure"
        FlightSortOption.ArrivalTime -> "arrival"
        FlightSortOption.Duration -> "duration"
        FlightSortOption.Fare -> "fare"
        FlightSortOption.Stops -> "stops"
    }

/**
 * Preserves search fields when building sort/date/pager links.
 * Uses raw `from`/`to` strings so the browser round-trips exactly what the user submitted.
 */
internal fun buildBaseParams(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): Map<String, String> {
    val preservedParams = LinkedHashMap<String, String>()
    if (fromRaw.isNotBlank()) preservedParams["from"] = fromRaw
    if (toRaw.isNotBlank()) preservedParams["to"] = toRaw
    if (departRaw.isNotBlank()) preservedParams["depart"] = departRaw
    queryParams["trip"]?.takeIf { it.isNotBlank() }?.let { preservedParams["trip"] = it }
    queryParams["cabinClass"]?.takeIf { it.isNotBlank() }?.let { preservedParams["cabinClass"] = it }
    queryParams["adults"]?.takeIf { it.isNotBlank() }?.let { preservedParams["adults"] = it }
    queryParams["children"]?.takeIf { it.isNotBlank() }?.let { preservedParams["children"] = it }
    queryParams["return"]?.takeIf { it.isNotBlank() }?.let { preservedParams["return"] = it }
    queryParams["leg"]?.takeIf { it.isNotBlank() }?.let { preservedParams["leg"] = it }
    queryParams["obFrom"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFrom"] = it }
    queryParams["obTo"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obTo"] = it }
    queryParams["obDepart"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obDepart"] = it }
    /** Inbound results URL carries the chosen outbound flight + tier alongside the inbound route. */
    queryParams["flight"]?.takeIf { it.isNotBlank() }?.let { preservedParams["flight"] = it }
    queryParams["fare"]?.takeIf { it.isNotBlank() }?.let { preservedParams["fare"] = it }
    queryParams["obFlight"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFlight"] = it }
    queryParams["obFare"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFare"] = it }
    queryParams["outboundPrice"]?.takeIf { it.isNotBlank() }?.let { preservedParams["outboundPrice"] = it }
    queryParams["dateStart"]?.takeIf { it.isNotBlank() }?.let { preservedParams["dateStart"] = it }
    return preservedParams
}

/**
 * Outbound leg search for a return trip: `from`/`to`/`depart` are the outbound airports and date;
 * `return` is the inbound travel date. Omits `leg` and `ob*` so the results page is the departing-flight list.
 *
 * On the **inbound** results URL, `return` is often empty because the client clears it when opening the
 * inbound leg; the inbound travel date still appears as `depart`. We copy that into `return` here so
 * outbound cards populate `data-search-return` and fare selection continues to the inbound search
 * instead of `/book/passengers`.
 */
internal fun buildOutboundLegSearchParams(queryParams: Parameters): Map<String, String> {
    val outboundParams = LinkedHashMap<String, String>()
    val obFrom = queryParams["obFrom"].orEmpty()
    val obTo = queryParams["obTo"].orEmpty()
    val obDepart = queryParams["obDepart"].orEmpty()
    val leg = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
    val departAny = queryParams["depart"].orEmpty()
    val retExplicit = queryParams["return"].orEmpty()
    val returnDate =
        when {
            retExplicit.isNotBlank() -> retExplicit
            leg == "inbound" && departAny.isNotBlank() -> departAny
            else -> ""
        }
    if (obFrom.isNotBlank()) outboundParams["from"] = obFrom
    if (obTo.isNotBlank()) outboundParams["to"] = obTo
    if (obDepart.isNotBlank()) outboundParams["depart"] = obDepart
    if (returnDate.isNotBlank()) outboundParams["return"] = returnDate
    outboundParams["trip"] = "return"
    queryParams["cabinClass"]?.takeIf { it.isNotBlank() }?.let { outboundParams["cabinClass"] = it }
    queryParams["adults"]?.takeIf { it.isNotBlank() }?.let { outboundParams["adults"] = it }
    queryParams["children"]?.takeIf { it.isNotBlank() }?.let { outboundParams["children"] = it }
    outboundParams["page"] = "1"
    return outboundParams
}

/**
 * Builds `/search-flights?…` with UTF-8 percent-encoding.
 * Spaces become `%20` (not `+`) so pasted links and strict parsers behave consistently.
 */
internal fun flightsHref(params: Map<String, String>): String {
    if (params.isEmpty()) return "/search-flights"
    val encodeQueryPart: (String) -> String = { part ->
        URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val queryString =
        params.entries.joinToString("&") { (key, value) ->
            "${encodeQueryPart(key)}=${encodeQueryPart(value)}"
        }
    return "/search-flights?$queryString"
}

/** `742` → `"12h 22m"` (total journey / card summary). */
internal fun formatDurationMinutes(min: Int): String {
    val hours = min / MINUTES_PER_HOUR
    val minutes = min % MINUTES_PER_HOUR
    return "${hours}h ${minutes}m"
}

/** Layover in route details, e.g. `14h 30 min` or `45 min`. */
internal fun formatLayoverDuration(min: Int): String {
    val hours = min / MINUTES_PER_HOUR
    val minutes = min % MINUTES_PER_HOUR
    return when {
        hours > 0 && minutes > 0 -> "${hours}h $minutes min"
        hours > 0 -> "${hours}h"
        else -> "$minutes min"
    }
}

internal fun formatMoney(gbp: BigDecimal): String = gbp.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** Always `HH:mm` (e.g. `00:15`, `08:20`) for consistent display. */
internal fun formatTime(time: LocalTime): String = String.format(Locale.UK, "%02d:%02d", time.hour, time.minute)

/** Economy / business / first tier prices for a schedule row (before column invariants). */
internal fun cabinFareSet(
    row: FlightScheduleRecord,
    cabinRaw: String,
): Map<String, BigDecimal> {
    val economyLight = row.priceLight
    val economyEssential = row.priceEssential
    val economyFlex = row.priceFlex
    val fares =
        when (cabinRaw) {
            "business" -> {
                val businessEssential = (economyLight * BigDecimal("5.00")).setScale(2, RoundingMode.HALF_UP)
                val businessFlex =
                    (businessEssential + maxOf(BigDecimal("150.00"), economyLight * BigDecimal("1.10")))
                        .setScale(2, RoundingMode.HALF_UP)
                mapOf(
                    "from" to businessEssential,
                    "light" to businessEssential,
                    "essential" to businessEssential,
                    "flex" to businessFlex,
                )
            }
            "first" -> {
                val firstFlex = (economyLight * BigDecimal("8.00")).setScale(2, RoundingMode.HALF_UP)
                mapOf(
                    "from" to firstFlex,
                    "light" to firstFlex,
                    "essential" to firstFlex,
                    "flex" to firstFlex,
                )
            }
            else ->
                mapOf(
                    "from" to economyLight,
                    "light" to economyLight,
                    "essential" to economyEssential,
                    "flex" to economyFlex,
                )
        }
    return enforceFareInvariants(fares, cabinRaw)
}

internal fun enforceFareInvariants(
    fares: Map<String, BigDecimal>,
    cabinRaw: String,
): Map<String, BigDecimal> {
    val light = fares.getValue("light")
    val essential = fares.getValue("essential")
    val flex = fares.getValue("flex")
    val fixedEssential = if (essential < light) light else essential
    val fixedFlex = if (flex <= fixedEssential) fixedEssential + BigDecimal("1.00") else flex
    val from =
        when (cabinRaw) {
            "first" -> fixedFlex
            "business" -> fixedEssential
            else -> light
        }
    return mapOf(
        "from" to from.setScale(2, RoundingMode.HALF_UP),
        "light" to light.setScale(2, RoundingMode.HALF_UP),
        "essential" to fixedEssential.setScale(2, RoundingMode.HALF_UP),
        "flex" to fixedFlex.setScale(2, RoundingMode.HALF_UP),
    )
}

internal fun flightCardMap(
    row: FlightScheduleRecord,
    cabinRaw: String,
): Map<String, Any?> {
    val legFlightNumbersKey = row.legFlightNumbers.joinToString("-")
    val cardDomId =
        encAttr(
            "${row.originCode}-${row.destCode}-${row.departDate}-$legFlightNumbersKey-${row.departTime}",
        )
    val arrivalPlusDays = row.arrivalOffsetDays.coerceAtLeast(0)
    val fares = cabinFareSet(row, cabinRaw)
    val originTimeZoneLabel = timeZoneLabel(row.originCode, row.departDate, row.departTime)
    val destTimeZoneLabel =
        timeZoneLabel(row.destCode, row.departDate.plusDays(arrivalPlusDays.toLong()), row.arrivalTime)
    val departed =
        runCatching {
            val zone = AirportTimeZoneResolver.zoneIdForIata(row.originCode)
            ZonedDateTime.of(row.departDate, row.departTime, zone).isBefore(ZonedDateTime.now(zone))
        }.getOrDefault(false)
    return mapOf(
        "id" to cardDomId,
        "departTime" to formatTime(row.departTime),
        "arrivalTime" to formatTime(row.arrivalTime),
        "arrivalPlusDays" to arrivalPlusDays,
        "originCode" to row.originCode,
        "destCode" to row.destCode,
        "originTimeZone" to originTimeZoneLabel,
        "destTimeZone" to destTimeZoneLabel,
        "timeZoneDifference" to timeZoneDifferenceLabel(row),
        "originFull" to FlightSearchRepository.airportNameForCode(row.originCode),
        "destFull" to FlightSearchRepository.airportNameForCode(row.destCode),
        "stopSummary" to stopSummary(row),
        "durationLabel" to formatDurationMinutes(row.durationMinutes),
        "stops" to row.stops,
        "flightNumberChain" to row.legFlightNumbers.joinToString(" → "),
        "fromPrice" to formatMoney(fares.getValue("from")),
        "priceLight" to formatMoney(fares.getValue("light")),
        "priceEssential" to formatMoney(fares.getValue("essential")),
        "priceFlex" to formatMoney(fares.getValue("flex")),
        "departed" to departed,
        "routeBlocks" to buildRouteBlocks(row),
        "timelineStops" to row.stopoverCodes.map { mapOf("code" to it) },
    )
}

/** One line under the timeline: direct or stop count only (no airports or layovers). */
internal fun stopSummary(row: FlightScheduleRecord): String =
    when (row.stops) {
        0 -> "Direct"
        1 -> "1 stop"
        else -> "${row.stops} stops"
    }

internal fun timeZoneLabel(
    airportCode: String,
    date: LocalDate,
    time: LocalTime,
): String {
    val zone = AirportTimeZoneResolver.zoneIdForIata(airportCode)
    val offset = ZonedDateTime.of(date, time, zone).offset
    return "UTC${offset.id.replace("Z", "+00:00")}"
}

internal fun timeZoneDifferenceLabel(row: FlightScheduleRecord): String {
    val originZone = AirportTimeZoneResolver.zoneIdForIata(row.originCode)
    val destZone = AirportTimeZoneResolver.zoneIdForIata(row.destCode)
    val departureInstant = ZonedDateTime.of(row.departDate, row.departTime, originZone).toInstant()
    val originOffset = originZone.rules.getOffset(departureInstant)
    val destOffset = destZone.rules.getOffset(departureInstant)
    val differenceMinutes = (destOffset.totalSeconds - originOffset.totalSeconds) / MINUTES_PER_HOUR
    if (differenceMinutes == 0) return "No time zone change"

    val sign = if (differenceMinutes > 0) "+" else "-"
    val absoluteMinutes = kotlin.math.abs(differenceMinutes)
    val hours = absoluteMinutes / MINUTES_PER_HOUR
    val minutes = absoluteMinutes % MINUTES_PER_HOUR
    val value =
        if (minutes == 0) {
            "$sign${hours}h"
        } else {
            "$sign${hours}h ${minutes}m"
        }
    return "Time zone change $value"
}

/** Colons break HTML ids; strip them so client `getElementById` still works. */
internal fun encAttr(rawId: String): String = rawId.replace(":", "-")

internal val dayChipFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/** Seven-day strip for the visible conveyor window, each linking back with the same search params. */
internal fun buildCarouselDays(
    selected: LocalDate,
    windowStart: LocalDate,
    base: Map<String, String>,
): List<Map<String, Any?>> {
    val today = LocalDate.now()
    return (0 until DATE_CAROUSEL_VISIBLE_DAYS).map { offset ->
        val chipDate = windowStart.plusDays(offset.toLong())
        val params = base.toMutableMap()
        params["depart"] = chipDate.toString()
        params["dateStart"] =
            chipDate
                .minusDays(DATE_CAROUSEL_CENTER_OFFSET_DAYS.toLong())
                .coerceAtLeast(today)
                .toString()
        params["page"] = "1"
        val past = chipDate.isBefore(today)
        mapOf(
            "label" to dayChipFmt.format(chipDate),
            "iso" to chipDate.toString(),
            "selected" to (chipDate == selected),
            "past" to past,
            "href" to if (past) "" else flightsHref(params),
        )
    }
}

/** Href for each sort tab; clicking the active tab toggles asc/desc (except Recommended). */
internal fun buildSortLinks(
    base: Map<String, String>,
    current: FlightSortOption,
    ascending: Boolean,
): Map<FlightSortOption, String> =
    FlightSortOption.entries.associateWith { key ->
        val nextOrder =
            when (key) {
                FlightSortOption.Recommended -> "asc"
                current -> if (ascending) "desc" else "asc"
                else -> "asc"
            }
        flightsHref(
            base +
                mapOf(
                    "sort" to key.toParam(),
                    "order" to nextOrder,
                    "page" to "1",
                ),
        )
    }
