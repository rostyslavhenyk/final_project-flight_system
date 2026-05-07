package routes.flight

import auth.UserSession
import data.BookingRepository
import data.PaymentRepository
import data.PurchaseRepository
import data.Seat
import data.SeatRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import routes.loggedIn
import routes.renderTemplate
import utils.jsMode
import utils.StripeService
import utils.timed
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Registers `/flights`, `/search-flights`, `/book/review`, `/book/passengers`, `/book/seats`, `/book/payment`. */
fun Route.flightsRoutes() {
    get("/flights") { call.handleFlightsPage() }
    get("/flight-status") { call.handleFlightStatusPage() }
    get("/api/flight-number-suggest") { call.handleFlightNumberSuggest() }
    get("/search-flights") { call.handleSearchFlightsList() }
    get("/book/review") { call.handleBookReview() }
    get("/book/passengers") { call.handleBookPassengers() }
    get("/book/seats") { call.handleBookSeats() }
    get("/book/seats/unavailable") { call.handleSeatUnavailable() }
    get("/book/seats/validate") { call.handleSeatSelectionValidate() }
    post("/book/seats/hold") { call.handleSeatHold() }
    post("/book/seats/release") { call.handleSeatRelease() }
    get("/book/payment") { call.handleBookPayment() }
    post("/book/payment/setup-intent") { call.handleBookPaymentSetupIntent() }
    post("/book/payment/confirm") { call.handleBookPaymentConfirm() }
}

/** Simple static Flights page so the header link does not clash with search results. */
private suspend fun ApplicationCall.handleFlightsPage() {
    timed("T0_flights_page", jsMode()) {
        respondRedirect("/")
    }
}

/** Parses query string, loads flights, sorts, pages, renders `flights/step-1-search-results/index.peb`. */
private suspend fun ApplicationCall.handleSearchFlightsList() {
    timed("T0_search_flights_list", jsMode()) {
        searchFlightsRedirectIfCanonicalCabinNeeded(request.queryParameters)?.let { url ->
            respondRedirect(url)
            return@timed
        }
        renderFlightTemplate(
            "user/flights/step-1-search-results/index.peb",
            searchFlightsModel(request.queryParameters),
        )
    }
}

/** After fare selection: recap still counts as step 1 (Choose flights) before passengers. */
private suspend fun ApplicationCall.handleBookReview() {
    timed("T0_book_review", jsMode()) {
        redirectToLoginIfNeeded()?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val queryParams = request.queryParameters
        bookPathRedirectIfCanonicalCabinNeeded("/book/review", queryParams)?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val inboundRow =
            findRecordForRouteAndFlightId(
                fromRaw = queryParams["from"].orEmpty(),
                toRaw = queryParams["to"].orEmpty(),
                departRaw = queryParams["depart"].orEmpty(),
                flightId = queryParams["flight"].orEmpty(),
            )
        if (inboundRow == null) {
            renderFlightTemplate(
                "user/flights/book-review-missing.peb",
                mapOf(
                    "title" to "Selection not found",
                    "backHref" to backToFlightSearchHref(queryParams),
                ),
            )
            return@timed
        }
        renderFlightTemplate("user/flights/book-review/index.peb", bookReviewModel(queryParams, inboundRow))
    }
}

/** Step 3: passenger details after `/book/review`. */
private suspend fun ApplicationCall.handleBookPassengers() {
    timed("T0_book_passengers", jsMode()) {
        redirectToLoginIfNeeded()?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val queryParams = request.queryParameters
        bookPathRedirectIfCanonicalCabinNeeded("/book/passengers", queryParams)?.let { url ->
            respondRedirect(url)
            return@timed
        }
        renderFlightTemplate(
            "user/flights/step-2-passengers/index.peb",
            bookPassengersModel(queryParams, request.local.uri, loggedIn()),
        )
    }
}

/** Step 3: seat map and extras (client-side selection; server model from chosen flights). */
private suspend fun ApplicationCall.handleBookSeats() {
    timed("T0_book_seats", jsMode()) {
        redirectToLoginIfNeeded()?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val queryParams = request.queryParameters
        bookPathRedirectIfCanonicalCabinNeeded("/book/seats", queryParams)?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val model = bookSeatsModel(queryParams)
        renderFlightTemplate("user/flights/step-3-seats/index.peb", model)
    }
}

/** Step 4: confirm seat fees and proceed to pay. */
private suspend fun ApplicationCall.handleBookPayment() {
    timed("T0_book_payment", jsMode()) {
        redirectToLoginIfNeeded()?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val queryParams = request.queryParameters
        bookPathRedirectIfCanonicalCabinNeeded("/book/payment", queryParams)?.let { url ->
            respondRedirect(url)
            return@timed
        }
        val model = bookPaymentModel(queryParams)
        renderFlightTemplate("user/flights/step-4-payment/index.peb", model)
    }
}

private suspend fun ApplicationCall.handleBookPaymentConfirm() {
    timed("T0_book_payment_confirm", jsMode()) {
        val session = sessions.get<UserSession>()
        if (session == null) {
            respond(HttpStatusCode.Unauthorized, "login-required")
            return@timed
        }

        val form = receiveParameters()
        val setupIntentId = form["setupIntentId"].orEmpty()
        if (!StripeService.setupIntentSucceeded(setupIntentId)) {
            respond(HttpStatusCode.PaymentRequired, "stripe-setup-required")
            return@timed
        }

        val queryParams = request.queryParameters
        val selectedSeats = selectedSeatRefs(queryParams)
        if (selectedSeats.isEmpty()) {
            respond(HttpStatusCode.BadRequest, "no-seats-selected")
            return@timed
        }

        val confirmedSeats =
            selectedSeats.mapNotNull { selectedSeat ->
                SeatRepository.createConfirmed(
                    userIdValue = session.id,
                    flightIdValue = selectedSeat.flightId,
                    row = selectedSeat.row,
                    seat = selectedSeat.seatLetter,
                )
            }
        if (confirmedSeats.size != selectedSeats.size) {
            respond(HttpStatusCode.Conflict, "seat-unavailable")
            return@timed
        }

        val model = bookPaymentModel(queryParams)
        val amount = (model["grandTotalPlain"] as? String)?.toDoubleOrNull() ?: 0.0
        val purchase =
            PurchaseRepository.create(
                userID = session.id,
                amount = amount,
                bookingQuery = request.queryString(),
            )
        PaymentRepository.create(
            purchaseID = purchase.purchaseID,
            amount = amount,
            paymentMethod = "ONLINE",
            paymentStatus = "PAID",
            transactionRef = "WEB-${UUID.randomUUID()}",
        )

        val createdBookings = createBookingsForConfirmedSeats(confirmedSeats, session.id, purchase.purchaseID)
        if (createdBookings == 0) {
            respond(HttpStatusCode.BadRequest, "no-seats-selected")
            return@timed
        }

        respond(HttpStatusCode.OK, "confirmed")
    }
}

private suspend fun ApplicationCall.handleBookPaymentSetupIntent() {
    timed("T0_book_payment_setup_intent", jsMode()) {
        val session = sessions.get<UserSession>()
        if (session == null) {
            respond(HttpStatusCode.Unauthorized, "login-required")
            return@timed
        }
        val intent = StripeService.createSetupIntent(session.id)
        if (intent == null) {
            respond(HttpStatusCode.ServiceUnavailable, "stripe-not-configured")
            return@timed
        }
        respondText(
            """{"publishableKey":"${StripeService.publishableKey}","clientSecret":"${intent.clientSecret}"}""",
            contentType = io.ktor.http.ContentType.Application.Json,
        )
    }
}

private fun createBookingsForConfirmedSeats(
    confirmedSeats: List<Seat>,
    userId: Int,
    purchaseId: Int,
): Int {
    var created = 0
    confirmedSeats.forEach { seat ->
        val booking =
            BookingRepository.create(
                flightID = seat.flightId,
                userID = userId,
                seatID = seat.id,
                status = "CONFIRMED",
            )
        BookingRepository.attachToPurchase(booking.bookingID, purchaseId)
        created++
    }
    return created
}

internal fun selectedOutboundRecord(queryParams: io.ktor.http.Parameters) =
    if (queryParams["trip"].equals("return", ignoreCase = true)) {
        findOutboundRecordForBooking(queryParams)
    } else {
        findRecordForBooking(queryParams)
    }

internal fun selectedInboundRecord(queryParams: io.ktor.http.Parameters) =
    if (queryParams["trip"].equals("return", ignoreCase = true)) {
        findRecordForBooking(queryParams)
    } else {
        null
    }

internal fun String.flightIdFromNumber(): Int? =
    trim()
        .uppercase()
        .removePrefix("GA")
        .toIntOrNull()

internal fun parseSeatId(seatId: String): Pair<Int, String>? {
    val row = seatId.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
    val letter = seatId.dropWhile { it.isDigit() }.take(1).uppercase()
    return if (letter.isBlank()) null else row to letter
}

private suspend fun ApplicationCall.renderFlightTemplate(
    templatePath: String,
    model: Map<String, Any?>,
) {
    renderTemplate(templatePath, model)
}

private fun ApplicationCall.redirectToLoginIfNeeded(): String? {
    if (loggedIn().loggedIn) return null
    val redirect = URLEncoder.encode(request.local.uri, StandardCharsets.UTF_8)
    return "/login?redirect=$redirect"
}
