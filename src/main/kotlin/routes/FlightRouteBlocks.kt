package routes

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightScheduleRecord

private const val DEFAULT_LAYOVER_MINUTES = 75

/**
 * Builds segment/connect rows for the Route details panel.
 * Times are local to each airport, and each leg keeps its own flight number.
 */
internal fun buildRouteBlocks(row: FlightScheduleRecord): List<Map<String, Any?>> {
    val codes = routeAirportCodes(row)
    val blocks = mutableListOf<Map<String, Any?>>()
    for (legIndex in 0 until row.stops + 1) {
        blocks.add(routeSegmentBlock(row, codes, legIndex))
        if (legIndex < row.stops) {
            blocks.add(connectionBlock(row, codes[legIndex + 1], legIndex))
        }
    }
    return blocks
}

private fun routeAirportCodes(row: FlightScheduleRecord): List<String> =
    buildList {
        add(row.originCode)
        addAll(row.stopoverCodes)
        add(row.destCode)
    }

private fun routeSegmentBlock(
    row: FlightScheduleRecord,
    codes: List<String>,
    legIndex: Int,
): Map<String, Any?> {
    val legDepart = row.legDepartureTimes[legIndex]
    val legArrive = row.legArrivalTimes[legIndex]
    val depPlusDays = departureOffsetDays(row, legIndex)
    val arrPlusDays = arrivalOffsetDays(row, legIndex, legDepart, legArrive)
    return mapOf(
        "kind" to "segment",
        "fromCode" to codes[legIndex],
        "toCode" to codes[legIndex + 1],
        "fromName" to FlightSearchRepository.airportNameForCode(codes[legIndex]),
        "toName" to FlightSearchRepository.airportNameForCode(codes[legIndex + 1]),
        "depart" to formatTime(legDepart),
        "depPlusDays" to depPlusDays,
        "arrive" to formatTime(legArrive),
        "arrPlusDays" to arrPlusDays,
        "flight" to row.legFlightNumbers[legIndex],
        "fromTimeZone" to timeZoneLabel(codes[legIndex], row.departDate.plusDays(depPlusDays.toLong()), legDepart),
        "toTimeZone" to timeZoneLabel(codes[legIndex + 1], row.departDate.plusDays(arrPlusDays.toLong()), legArrive),
    )
}

private fun departureOffsetDays(
    row: FlightScheduleRecord,
    legIndex: Int,
): Int =
    if (legIndex == 0) {
        0
    } else {
        val previousArrivalOffset = previousArrivalOffsetDays(row, legIndex)
        val wrapsToNextDay = row.legDepartureTimes[legIndex].isBefore(row.legArrivalTimes[legIndex - 1])
        previousArrivalOffset + if (wrapsToNextDay) 1 else 0
    }

private fun previousArrivalOffsetDays(
    row: FlightScheduleRecord,
    legIndex: Int,
): Int =
    row.legArrivalOffsetDays.getOrElse(legIndex - 1) {
        if (row.legArrivalTimes[legIndex - 1].isBefore(row.legDepartureTimes[legIndex])) 1 else 0
    }

private fun arrivalOffsetDays(
    row: FlightScheduleRecord,
    legIndex: Int,
    legDepart: java.time.LocalTime,
    legArrive: java.time.LocalTime,
): Int {
    val cumulativeArrivalOffset =
        row.legArrivalOffsetDays.getOrElse(legIndex) { if (legArrive.isBefore(legDepart)) 1 else 0 }
    return if (legIndex == row.stops) {
        maxOf(cumulativeArrivalOffset, row.arrivalOffsetDays.coerceAtLeast(0))
    } else {
        cumulativeArrivalOffset
    }
}

private fun connectionBlock(
    row: FlightScheduleRecord,
    stopoverAirportCode: String,
    legIndex: Int,
): Map<String, Any?> {
    val layoverMinutes = row.stopoverLayoverMinutes.getOrElse(legIndex) { DEFAULT_LAYOVER_MINUTES }
    return mapOf(
        "kind" to "connect",
        "airportCode" to stopoverAirportCode,
        "airportName" to FlightSearchRepository.airportNameForCode(stopoverAirportCode),
        "layoverLabel" to formatLayoverDuration(layoverMinutes),
    )
}
