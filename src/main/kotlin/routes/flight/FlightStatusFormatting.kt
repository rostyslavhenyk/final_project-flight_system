package routes.flight

import data.flight.FLIGHT_STATUS_UPCOMING_DAY_COUNT
import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightStatusRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun hasFlightStatusQuery(query: FlightStatusQuery): Boolean =
    when (query.mode) {
        "route" -> query.fromRaw.isNotBlank() || query.toRaw.isNotBlank()
        else -> query.flightNumberRaw.isNotBlank()
    }

internal fun statusResultsLabel(query: FlightStatusQuery): String =
    if (query.anyDate) {
        "Upcoming flights (next $FLIGHT_STATUS_UPCOMING_DAY_COUNT days)"
    } else {
        "Departing on ${query.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK))}"
    }

internal fun statusTotalFlightsLabel(totalRows: Int): String =
    when (totalRows) {
        0 -> "0 flights"
        1 -> "1 flight"
        else -> "$totalRows flights"
    }

internal fun noRouteStatusMessage(query: FlightStatusQuery): String =
    if (query.anyDate) {
        "No scheduled flights are available for this route in the next $FLIGHT_STATUS_UPCOMING_DAY_COUNT days."
    } else {
        "No scheduled flights are available for this route on ${shortDate(query.date)}."
    }

internal fun noFlightNumberStatusMessage(query: FlightStatusQuery): String =
    if (query.anyDate) {
        "No flights found for GA${query.flightDigitsOnly} in the next $FLIGHT_STATUS_UPCOMING_DAY_COUNT days."
    } else {
        "No flights found for GA${query.flightDigitsOnly} on ${shortDate(query.date)}."
    }

private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK))

internal fun scheduledTimeLabel(
    time: LocalTime,
    offsetDays: Int,
): String {
    val label = String.format(Locale.UK, "%02d:%02d", time.hour, time.minute)
    return if (offsetDays > 0) "$label +$offsetDays" else label
}

internal fun estimatedTimeLabel(
    time: LocalTime?,
    offsetDays: Int?,
): String? =
    time?.let { estimatedTime ->
        scheduledTimeLabel(estimatedTime, offsetDays ?: 0)
    }

internal fun statusLabel(statusCode: String): String =
    when (statusCode) {
        "DELAYED" -> "Delayed"
        "CANCELLED" -> "Cancelled"
        else -> "On time"
    }

internal fun statusClass(statusCode: String): String =
    when (statusCode) {
        "DELAYED" -> "status-badge--delay"
        "CANCELLED" -> "status-badge--cancelled"
        else -> "status-badge--ok"
    }

internal fun viaLine(statusResult: FlightStatusRecord): String? =
    if (statusResult.stopoverAirportCodes.isNotEmpty()) {
        statusResult.stopoverAirportCodes.joinToString(" - ") { airportCode ->
            "${FlightSearchRepository.airportNameForCode(airportCode)} ($airportCode)"
        }
    } else {
        null
    }
