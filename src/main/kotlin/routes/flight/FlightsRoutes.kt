package routes.flight

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import routes.loggedIn
import routes.renderTemplate
import utils.jsMode
import utils.timed
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Registers `/flights`, `/search-flights`, `/book/review`, `/book/passengers`, `/book/seats`, `/book/payment`. */
fun Route.flightsRoutes() {
    get("/flights") { call.handleFlightsPage() }
    get("/flight-status") { call.handleFlightStatusPage() }
    get("/api/flight-number-suggest") { call.handleFlightNumberSuggest() }
    get("/search-flights") { call.handleSearchFlightsList() }
    get("/book/review") { call.handleBookReview() }
    get("/book/passengers") { call.handleBookPassengers() }
    get("/book/seats") { call.handleBookSeats() }
    get("/book/payment") { call.handleBookPayment() }
}

/** Simple static Flights page so the header link does not clash with search results. */
private suspend fun ApplicationCall.handleFlightsPage() {
    timed("T0_flights_page", jsMode()) {
        val model = mapOf("title" to "Flights")
        renderFlightTemplate("user/flights/index.peb", model)
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
        renderFlightTemplate("flights/step-4-payment/index.peb", model)
    }
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
