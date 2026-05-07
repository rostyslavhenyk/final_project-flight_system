package routes.flight

import data.flight.FLIGHT_STATUS_UPCOMING_DAY_COUNT
import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightStatusRecord
import data.flight.FlightStatusSearchRepository
import io.ktor.http.Parameters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FLIGHT_DIGIT_LIMIT = 4

internal data class FlightStatusQuery(
    val mode: String,
    val today: LocalDate,
    val date: LocalDate,
    val anyDate: Boolean,
    val flightNumberRaw: String,
    val flightDigitsOnly: String,
    val flightNumber: String,
    val fromRaw: String,
    val toRaw: String,
    val fromCode: String?,
    val toCode: String?,
    val page: Int,
)

internal fun flightStatusPageModel(parameters: Parameters): Map<String, Any?> {
    val query = parseFlightStatusQuery(parameters)
    val statusRowsByDate = statusRowsByDate(query)
    val rowMaps = statusRowsByDate.map { (serviceDate, statusResult) -> statusRowMap(serviceDate, statusResult, query) }
    val pageCount = maxOf(1, (rowMaps.size + STATUS_RESULTS_PAGE_SIZE - 1) / STATUS_RESULTS_PAGE_SIZE)
    val safePage = query.page.coerceIn(1, pageCount)
    val pagedRows = rowMaps.drop((safePage - 1) * STATUS_RESULTS_PAGE_SIZE).take(STATUS_RESULTS_PAGE_SIZE)
    val hasQuery = hasFlightStatusQuery(query)

    return mapOf(
        "title" to "Flight status",
        "mode" to query.mode,
        "date" to query.date.toString(),
        "minDate" to query.today.toString(),
        "anyDate" to query.anyDate,
        "flightNumber" to query.flightNumber,
        "flightDigits" to query.flightDigitsOnly,
        "fromRaw" to query.fromRaw,
        "toRaw" to query.toRaw,
        "airports" to FlightSearchRepository.airportLabels(),
        "hasQuery" to hasQuery,
        "formError" to statusFormError(query, statusRowsByDate, hasQuery),
        "statusRows" to pagedRows,
        "resultsLabel" to statusResultsLabel(query),
        "statusPagerShow" to (pageCount > 1),
        "statusPagerPages" to statusPagerPages(query, pageCount, safePage),
        "statusTotalFlightsLabel" to statusTotalFlightsLabel(rowMaps.size),
    )
}

private fun parseFlightStatusQuery(parameters: Parameters): FlightStatusQuery {
    val mode = parameters["mode"]?.lowercase(Locale.UK)?.trim().takeUnless { it.isNullOrBlank() } ?: "flight-number"
    val today = LocalDate.now()
    val date =
        (runCatching { LocalDate.parse(parameters["date"].orEmpty()) }.getOrNull() ?: today)
            .let { parsedDate -> if (parsedDate.isBefore(today)) today else parsedDate }
    val flightNumberRaw = parameters["flightNumber"].orEmpty().trim().uppercase(Locale.UK)
    val flightDigitsOnly = flightNumberRaw.filter { it.isDigit() }.take(FLIGHT_DIGIT_LIMIT)
    val fromRaw = parameters["from"].orEmpty()
    val toRaw = parameters["to"].orEmpty()
    return FlightStatusQuery(
        mode = mode,
        today = today,
        date = date,
        anyDate = parameters["anyDate"] == "1",
        flightNumberRaw = flightNumberRaw,
        flightDigitsOnly = flightDigitsOnly,
        flightNumber = if (flightDigitsOnly.isBlank()) "" else "GA$flightDigitsOnly",
        fromRaw = fromRaw,
        toRaw = toRaw,
        fromCode = FlightSearchRepository.resolveAirportCode(fromRaw),
        toCode = FlightSearchRepository.resolveAirportCode(toRaw),
        page = parameters["page"]?.toIntOrNull() ?: 1,
    )
}

private fun statusRowsByDate(query: FlightStatusQuery): List<Pair<LocalDate, FlightStatusRecord>> {
    val searchDates =
        if (query.anyDate) {
            (0 until FLIGHT_STATUS_UPCOMING_DAY_COUNT).map { query.today.plusDays(it.toLong()) }
        } else {
            listOf(query.date)
        }
    return when (query.mode) {
        "route" -> routeStatusRows(searchDates, query)
        else -> flightNumberStatusRows(searchDates, query)
    }
}

private fun routeStatusRows(
    searchDates: List<LocalDate>,
    query: FlightStatusQuery,
): List<Pair<LocalDate, FlightStatusRecord>> =
    if (query.fromCode != null && query.toCode != null && searchDates.isNotEmpty()) {
        FlightStatusSearchRepository.statusByRouteAcrossDates(query.fromCode, query.toCode, searchDates)
    } else {
        emptyList()
    }

private fun flightNumberStatusRows(
    searchDates: List<LocalDate>,
    query: FlightStatusQuery,
): List<Pair<LocalDate, FlightStatusRecord>> =
    if (query.flightNumber.isNotBlank() && searchDates.isNotEmpty()) {
        FlightStatusSearchRepository.statusByFlightNumberAcrossDates(query.flightNumber, searchDates)
    } else {
        emptyList()
    }

private fun statusRowMap(
    serviceDate: LocalDate,
    statusResult: FlightStatusRecord,
    query: FlightStatusQuery,
): Map<String, Any?> {
    val statusCode = statusResult.statusCode.uppercase(Locale.UK)
    val estimatedDepartLabel =
        estimatedTimeLabel(statusResult.estimatedDepartTime, statusResult.estimatedDepartureOffsetDays)
    val estimatedArrivalLabel =
        estimatedTimeLabel(statusResult.estimatedArrivalTime, statusResult.estimatedArrivalOffsetDays)
    val viaLine = viaLine(statusResult)
    return mapOf(
        "flightNumber" to statusResult.flightNumber,
        "originCode" to statusResult.originCode,
        "destCode" to statusResult.destCode,
        "originName" to FlightSearchRepository.airportNameForCode(statusResult.originCode),
        "destName" to FlightSearchRepository.airportNameForCode(statusResult.destCode),
        "departureLabel" to scheduledTimeLabel(statusResult.departTime, statusResult.departureOffsetDays),
        "arrivalLabel" to scheduledTimeLabel(statusResult.arrivalTime, statusResult.arrivalOffsetDays),
        "estimatedDepartureLabel" to estimatedDepartLabel,
        "estimatedArrivalLabel" to estimatedArrivalLabel,
        "hasEstimate" to (estimatedDepartLabel != null && estimatedArrivalLabel != null),
        "statusLabel" to statusLabel(statusCode),
        "statusClass" to statusClass(statusCode),
        "serviceDateLabel" to serviceDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)),
        "showServiceDate" to query.anyDate,
        "hasVia" to (viaLine != null),
        "viaLine" to viaLine,
    )
}

private fun statusFormError(
    query: FlightStatusQuery,
    rows: List<Pair<LocalDate, FlightStatusRecord>>,
    hasQuery: Boolean,
): String? =
    when (query.mode) {
        "route" -> routeStatusFormError(query, rows, hasQuery)
        else -> flightNumberStatusFormError(query, rows, hasQuery)
    }

private fun routeStatusFormError(
    query: FlightStatusQuery,
    rows: List<Pair<LocalDate, FlightStatusRecord>>,
    hasQuery: Boolean,
): String? =
    when {
        !hasQuery -> null
        query.fromRaw.isBlank() || query.toRaw.isBlank() -> "Enter both origin and destination to check route status."
        query.fromCode == null || query.toCode == null ->
            "Please choose valid airport names/codes for both origin and destination."
        rows.isEmpty() -> noRouteStatusMessage(query)
        else -> null
    }

private fun flightNumberStatusFormError(
    query: FlightStatusQuery,
    rows: List<Pair<LocalDate, FlightStatusRecord>>,
    hasQuery: Boolean,
): String? =
    when {
        !hasQuery -> null
        query.flightDigitsOnly.isBlank() -> "Enter the flight number digits after GA (for example: 1285)."
        rows.isEmpty() -> noFlightNumberStatusMessage(query)
        else -> null
    }

private fun statusPagerPages(
    query: FlightStatusQuery,
    pageCount: Int,
    safePage: Int,
): List<Map<String, Any>> =
    (1..pageCount).map { pageNumber ->
        mapOf(
            "pageNum" to pageNumber,
            "href" to "/flight-status?${flightStatusQueryString(
                query.mode,
                query.date,
                query.anyDate,
                query.flightDigitsOnly,
                query.fromRaw,
                query.toRaw,
                pageNumber,
            )}",
            "isCurrent" to (pageNumber == safePage),
        )
    }
