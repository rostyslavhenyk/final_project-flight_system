package routes

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightScheduleRecord
import io.ktor.http.Parameters
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val LIGHT_SEAT_SELECTION_FEE_GBP = 30 // keep in sync with BookPaymentPageModels.LIGHT_JOURNEY_SEAT_FEE_GBP

private data class SeatJourney(
    val key: String,
    val tabLabel: String,
    val row: FlightScheduleRecord,
    val isLightFare: Boolean,
) {
    fun routeLine(): String {
        val o = row.originCode
        val d = row.destCode
        return "${FlightSearchRepository.cityForCode(o)} ($o) to ${FlightSearchRepository.cityForCode(d)} ($d)"
    }

    fun flightChain(): String = row.legFlightNumbers.joinToString(" · ")

    fun hasBusinessCabin(): Boolean = row.durationMinutes >= 360 || row.stops > 0
}

internal fun bookSeatsModel(queryParams: Parameters): Map<String, Any?> {
    val passengerRows = bookingPassengerRowModels(queryParams)
    val journeys = buildSeatJourneys(queryParams)
    val json = seatJourneysToJson(journeys)
    val b64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    val dualReturn = journeys.size > 1
    val outboundTier =
        effectiveFareTier(
            if (dualReturn) {
                queryParams["obFare"].orEmpty()
            } else {
                queryParams["fare"].orEmpty()
            },
        )
    val returnTier = effectiveFareTier(queryParams["fare"].orEmpty())
    val outboundLight = outboundTier.equals("light", ignoreCase = true)
    val inboundLight = returnTier.equals("light", ignoreCase = true)
    val showLightSeatFeeNote = outboundLight || (dualReturn && inboundLight)
    val seatJourneySummaries =
        journeys.map { j ->
            mapOf(
                "key" to j.key,
                "flightsLine" to "FLIGHT ${j.flightChain()}",
                "routeLine" to j.routeLine(),
            )
        }
    return mapOf(
        "title" to "Seat and extras",
        "chooseFlightsHref" to backToFlightSearchHref(queryParams),
        "passengersHref" to bookingHref("/book/passengers", queryParams),
        "continuePaymentHref" to bookingHref("/book/payment", queryParams),
        "passengerRows" to passengerRows,
        "hasReturnJourneys" to dualReturn,
        "hasSeatContext" to journeys.isNotEmpty(),
        "seatJourneySummaries" to seatJourneySummaries,
        "showLightSeatFeeNote" to showLightSeatFeeNote,
        "lightSeatFeeGbp" to LIGHT_SEAT_SELECTION_FEE_GBP,
        "seatJourneysB64" to b64,
        "seatRows" to (1..30).toList(),
        "seatLettersLeft" to listOf("A", "B", "C"),
        "seatLettersRight" to listOf("D", "E", "F"),
    )
}

private fun buildSeatJourneys(queryParams: Parameters): List<SeatJourney> {
    val inboundRow = findRecordForBooking(queryParams)
    val outboundRow = findOutboundRecordForBooking(queryParams)
    val dual =
        queryParams["trip"].equals("return", ignoreCase = true) &&
            outboundRow != null &&
            inboundRow != null
    if (inboundRow == null) return emptyList()
    val obTier =
        effectiveFareTier(
            if (dual) {
                queryParams["obFare"].orEmpty()
            } else {
                queryParams["fare"].orEmpty()
            },
        )
    val ibTier = effectiveFareTier(queryParams["fare"].orEmpty())
    val obLight = obTier.equals("light", ignoreCase = true)
    val ibLight = ibTier.equals("light", ignoreCase = true)
    return buildList {
        if (dual) {
            add(SeatJourney("outbound", "Outbound", outboundRow!!, obLight))
            add(SeatJourney("inbound", "Return", inboundRow, ibLight))
        } else {
            add(SeatJourney("outbound", "Outbound", inboundRow, obLight))
        }
    }
}

private fun legsForSchedule(row: FlightScheduleRecord): List<Map<String, Any?>> {
    val codes =
        buildList {
            add(row.originCode)
            addAll(row.stopoverCodes)
            add(row.destCode)
        }
    return List(row.stops + 1) { legIndex ->
        val fromC = codes[legIndex]
        val toC = codes[legIndex + 1]
        val fn = row.legFlightNumbers[legIndex]
        mapOf(
            "index" to legIndex,
            "flightNumber" to fn,
            "fromCode" to fromC,
            "toCode" to toC,
            "legLabel" to "FLIGHT $fn | $fromC to $toC",
        )
    }
}

private fun jsonEscape(s: String): String =
    s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

private fun jsonQuote(s: String): String = "\"" + jsonEscape(s) + "\""

private fun seatJourneysToJson(journeys: List<SeatJourney>): String =
    journeys.joinToString(prefix = "[", postfix = "]") { journey ->
        val legs = legsForSchedule(journey.row)
        val legsJson =
            legs.joinToString(",") { leg ->
                val idx = leg["index"] as Int
                val fn = leg["flightNumber"] as String
                val fromC = leg["fromCode"] as String
                val toC = leg["toCode"] as String
                val lbl = leg["legLabel"] as String
                """{"index":$idx,"flightNumber":${jsonQuote(fn)},"fromCode":${jsonQuote(fromC)},"toCode":${jsonQuote(
                    toC,
                )},"legLabel":${jsonQuote(lbl)}}"""
            }
        """{"key":${jsonQuote(journey.key)},"tabLabel":${jsonQuote(journey.tabLabel)},"routeLine":${jsonQuote(
            journey.routeLine(),
        )},"flightChain":${jsonQuote(
            journey.flightChain(),
        )},"hasBusinessCabin":${journey.hasBusinessCabin()},"isLightFare":${journey.isLightFare},"legs":[$legsJson]}"""
    }
