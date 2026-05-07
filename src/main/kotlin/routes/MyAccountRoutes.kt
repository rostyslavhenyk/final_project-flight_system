package routes

import auth.UserSession
import data.Purchase
import data.PurchaseRepository
import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import routes.flight.bookPaymentModel
import utils.jsMode
import utils.timed
import java.time.LocalDate
import java.util.Locale

private const val MEMBER_NUMBER_FORMAT = "GA%06d"

fun Route.myAccountRoutes() {
    get("/my-account") { call.handleMyAccountLoad() }
    post("/my-account/name") { call.handleMyAccountNameUpdate() }
    get("/account/fare-summary") { call.handleFareSummaryFragment() }
    get("/account/fare-summary/{purchaseId}") { call.handleSavedFareSummaryFragment() }
}

private suspend fun ApplicationCall.handleMyAccountLoad() {
    timed("T2_account_load", jsMode()) {
        val loggedState = loggedIn()

        if (!loggedState.loggedIn || loggedState.session == null) {
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        val account = UserRepository.get(loggedState.session.id)

        if (account == null) {
            sessions.clear<UserSession>()
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        if (account.roleId in setOf(1, 2)) {
            respondRedirect("/staff/my-account")
            return@timed
        }

        renderTemplate(
            "user/my-account/index.peb",
            mapOf(
                "title" to "My Account",
                "account" to account,
                "membershipNumber" to membershipNumberFor(account.id),
                "nameStatus" to request.queryParameters["nameStatus"].orEmpty(),
                "serverBookings" to accountBookingCards(account.id, past = false),
                "pastBookings" to accountBookingCards(account.id, past = true),
                "layout" to "_layout/base.peb",
            ),
        )
    }
}

private fun membershipNumberFor(userId: Int): String = String.format(Locale.UK, MEMBER_NUMBER_FORMAT, userId)

private fun accountBookingCards(
    userId: Int,
    past: Boolean,
): List<Map<String, String>> {
    val today = LocalDate.now()
    return PurchaseRepository
        .allByUser(userId)
        .filter { !it.bookingQuery.isNullOrBlank() }
        .filter { purchase ->
            val endDate = purchase.travelEndDate()
            val isPast = endDate != null && endDate.isBefore(today)
            isPast == past
        }.sortedWith(
            compareBy<Purchase> { purchaseDepartSort(it) }
                .thenByDescending { it.createdAt },
        ).mapIndexed { idx, purchase -> purchase.toBookingCard(idx) }
}

private fun purchaseDepartSort(purchase: Purchase): Long {
    val depart = parseQueryString(purchase.bookingQuery.orEmpty())["depart"]?.trim().orEmpty()
    return runCatching { LocalDate.parse(depart).toEpochDay() }.getOrDefault(Long.MAX_VALUE)
}

private fun Purchase.travelEndDate(): LocalDate? {
    val params = parseQueryString(bookingQuery.orEmpty())
    val endRaw =
        if (params["trip"].equals("return", ignoreCase = true)) {
            params["return"]?.trim().orEmpty().ifBlank { params["depart"]?.trim().orEmpty() }
        } else {
            params["depart"]?.trim().orEmpty()
        }
    return runCatching { LocalDate.parse(endRaw) }.getOrNull()
}

private fun Purchase.toBookingCard(index: Int): Map<String, String> {
    val params = parseQueryString(bookingQuery.orEmpty())
    val fromRaw = params["from"]?.trim().orEmpty()
    val toRaw = params["to"]?.trim().orEmpty()
    val depart = params["depart"]?.trim().orEmpty()
    val trip = if (params["trip"].equals("return", ignoreCase = true)) "Return" else "One way"
    val adults = params["adults"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val children = params["children"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val route =
        when {
            fromRaw.isNotBlank() && toRaw.isNotBlank() -> "$fromRaw -> $toRaw"
            fromRaw.isNotBlank() -> fromRaw
            toRaw.isNotBlank() -> toRaw
            else -> "Your flight"
        }
    val paxParts =
        buildList {
            add("$adults ${if (adults == 1) "adult" else "adults"}")
            if (children > 0) add("$children ${if (children == 1) "child" else "children"}")
        }
    val meta =
        buildList {
            if (depart.isNotBlank()) add(depart)
            add(trip)
            add(paxParts.joinToString(", "))
        }.joinToString(" | ")

    return mapOf(
        "label" to if (index == 0) "Latest booking" else "Booking ${index + 1}",
        "route" to route,
        "meta" to meta,
        "href" to "/account/fare-summary/$purchaseID",
    )
}

private suspend fun ApplicationCall.handleMyAccountNameUpdate() {
    timed("T2_account_name_update", jsMode()) {
        val loggedState = loggedIn()
        if (!loggedState.loggedIn || loggedState.session == null) {
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        val account = UserRepository.get(loggedState.session.id)
        if (account == null) {
            sessions.clear<UserSession>()
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }
        if (account.roleId in setOf(1, 2)) {
            respondRedirect("/staff/my-account")
            return@timed
        }

        val form = receiveParameters()
        val firstName = form["firstName"]?.trim().orEmpty()
        val lastName = form["lastName"]?.trim().orEmpty()
        if (firstName.isBlank() || lastName.isBlank()) {
            respondRedirect("/my-account?nameStatus=missing#tab-account-details")
            return@timed
        }

        val updated = UserRepository.updateName(account.id, firstName, lastName)
        if (updated == null) {
            respondRedirect("/my-account?nameStatus=error#tab-account-details")
            return@timed
        }

        respondRedirect("/my-account?nameStatus=updated#tab-account-details")
    }
}

private suspend fun ApplicationCall.handleFareSummaryFragment() {
    timed("T2_account_fare_summary", jsMode()) {
        val loggedState = loggedIn()

        if (!loggedState.loggedIn || loggedState.session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val account = UserRepository.get(loggedState.session.id)

        if (account == null || account.roleId in setOf(1, 2)) {
            respond(HttpStatusCode.NotFound)
            return@timed
        }

        renderTemplate(
            "user/account/fare-summary-fragment.peb",
            bookPaymentModel(request.queryParameters),
        )
    }
}

private suspend fun ApplicationCall.handleSavedFareSummaryFragment() {
    timed("T2_account_saved_fare_summary", jsMode()) {
        val loggedState = loggedIn()

        if (!loggedState.loggedIn || loggedState.session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val account = UserRepository.get(loggedState.session.id)

        if (account == null || account.roleId in setOf(1, 2)) {
            respond(HttpStatusCode.NotFound)
            return@timed
        }

        val purchaseId = parameters["purchaseId"]?.toIntOrNull()
        if (purchaseId == null) {
            respond(HttpStatusCode.BadRequest)
            return@timed
        }

        val purchase = PurchaseRepository.get(purchaseId)
        if (purchase == null || purchase.userID != account.id || purchase.bookingQuery.isNullOrBlank()) {
            respond(HttpStatusCode.NotFound)
            return@timed
        }

        renderTemplate(
            "user/account/fare-summary-fragment.peb",
            bookPaymentModel(parseQueryString(purchase.bookingQuery)),
        )
    }
}
