package routes.flight

import auth.UserSession
import data.Seat
import data.SeatRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import utils.jsMode
import utils.timed

internal suspend fun ApplicationCall.handleSeatUnavailable() {
    timed("T0_book_seats_unavailable", jsMode()) {
        val session = sessions.get<UserSession>()
        val flightRefs = selectedJourneyFlightRefs(request.queryParameters)
        val unavailableSeats =
            SeatRepository.unavailableForFlights(
                flightIds = flightRefs.map { ref -> ref.flightId }.toSet(),
                userIdValue = session?.id,
            )
        respondText(seatUnavailableJson(unavailableSeats, flightRefs))
    }
}

internal suspend fun ApplicationCall.handleSeatSelectionValidate() {
    timed("T0_book_seats_validate", jsMode()) {
        val session = sessions.get<UserSession>()
        val unavailableRefs =
            selectedSeatRefs(request.queryParameters)
                .filter { ref ->
                    SeatRepository.isUnavailableForUser(
                        flightIdValue = ref.flightId,
                        row = ref.row,
                        seat = ref.seatLetter,
                        userIdValue = session?.id,
                    )
                }
        respondText(selectedUnavailableSeatJson(unavailableRefs))
    }
}

internal suspend fun ApplicationCall.handleSeatHold() {
    timed("T0_book_seats_hold", jsMode()) {
        val session = sessions.get<UserSession>()
        if (session == null) {
            respond(HttpStatusCode.Unauthorized, "login-required")
            return@timed
        }
        val ref = selectedSeatRef(request.queryParameters, receiveParameters())
        if (ref == null) {
            respond(HttpStatusCode.BadRequest, "invalid-seat")
            return@timed
        }
        val held =
            SeatRepository.hold(
                userIdValue = session.id,
                flightIdValue = ref.flightId,
                row = ref.row,
                seat = ref.seatLetter,
            )
        val status = if (held) HttpStatusCode.OK else HttpStatusCode.Conflict
        respond(status, if (held) "held" else "seat-unavailable")
    }
}

internal suspend fun ApplicationCall.handleSeatRelease() {
    timed("T0_book_seats_release", jsMode()) {
        val session = sessions.get<UserSession>()
        if (session == null) {
            respond(HttpStatusCode.Unauthorized, "login-required")
            return@timed
        }
        val ref = selectedSeatRef(request.queryParameters, receiveParameters())
        if (ref == null) {
            respond(HttpStatusCode.BadRequest, "invalid-seat")
            return@timed
        }
        SeatRepository.release(session.id, ref.flightId, ref.row, ref.seatLetter)
        respond(HttpStatusCode.OK, "released")
    }
}

internal fun selectedSeatRefs(queryParams: io.ktor.http.Parameters): List<SelectedSeatRef> {
    val flightRefs = selectedJourneyFlightRefs(queryParams)
    return decodeSeatSelection(queryParams["seatSel"])
        .flatMap { (journeyKey, legs) ->
            legs.flatMap { (legIndexRaw, passengerSeats) ->
                selectedSeatRefsForLeg(journeyKey, legIndexRaw, passengerSeats, flightRefs)
            }
        }
}

private fun selectedSeatRefsForLeg(
    journeyKey: String,
    legIndexRaw: String,
    passengerSeats: Map<String, String>,
    flightRefs: List<JourneyFlightRef>,
): List<SelectedSeatRef> {
    val legIndex = legIndexRaw.toIntOrNull()
    val flightId = flightRefs.find { ref -> ref.journeyKey == journeyKey && ref.legIndex == legIndex }?.flightId
    return passengerSeats.values.mapNotNull { seatId ->
        parseSeatId(seatId)?.let { seatParts ->
            flightId?.let { SelectedSeatRef(it, journeyKey, legIndex ?: 0, seatParts.first, seatParts.second) }
        }
    }
}

private fun selectedSeatRef(
    queryParams: io.ktor.http.Parameters,
    form: io.ktor.http.Parameters,
): SelectedSeatRef? {
    val journeyKey = form["journeyKey"].orEmpty()
    val legIndex = form["legIndex"]?.toIntOrNull()
    val seatParts = parseSeatId(form["seatId"].orEmpty())
    val flightId =
        selectedJourneyFlightRefs(queryParams)
            .find { ref -> ref.journeyKey == journeyKey && ref.legIndex == legIndex }
            ?.flightId
    return if (legIndex == null || seatParts == null || flightId == null) {
        null
    } else {
        SelectedSeatRef(flightId, journeyKey, legIndex, seatParts.first, seatParts.second)
    }
}

private fun selectedJourneyFlightRefs(queryParams: io.ktor.http.Parameters): List<JourneyFlightRef> =
    buildList {
        selectedOutboundRecord(queryParams)?.legFlightNumbers?.forEachIndexed { index, flightNumber ->
            flightNumber.flightIdFromNumber()?.let { flightId -> add(JourneyFlightRef("outbound", index, flightId)) }
        }
        selectedInboundRecord(queryParams)?.legFlightNumbers?.forEachIndexed { index, flightNumber ->
            flightNumber.flightIdFromNumber()?.let { flightId -> add(JourneyFlightRef("inbound", index, flightId)) }
        }
    }

private fun seatUnavailableJson(
    seats: List<Seat>,
    flightRefs: List<JourneyFlightRef>,
): String {
    val refsByFlight = flightRefs.groupBy { ref -> ref.flightId }
    val items =
        seats.flatMap { seat ->
            refsByFlight[seat.flightId].orEmpty().map { ref -> seatUnavailableItemJson(seat, ref) }
        }
    return """{"seats":[${items.joinToString(",")}]}"""
}

private fun seatUnavailableItemJson(
    seat: Seat,
    ref: JourneyFlightRef,
): String =
    """{"journeyKey":"${ref.journeyKey}","legIndex":${ref.legIndex},"seatId":"${seat.rowNumber}${seat.seatLetter}"}"""

private fun selectedUnavailableSeatJson(refs: List<SelectedSeatRef>): String {
    val items =
        refs.joinToString(",") { ref ->
            """{"journeyKey":"${ref.journeyKey}","legIndex":${ref.legIndex},"seatId":"${ref.row}${ref.seatLetter}"}"""
        }
    return """{"seats":[$items]}"""
}

private data class JourneyFlightRef(
    val journeyKey: String,
    val legIndex: Int,
    val flightId: Int,
)

internal data class SelectedSeatRef(
    val flightId: Int,
    val journeyKey: String,
    val legIndex: Int,
    val row: Int,
    val seatLetter: String,
)
