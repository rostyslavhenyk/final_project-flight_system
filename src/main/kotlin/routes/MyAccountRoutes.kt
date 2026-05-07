package routes

import auth.UserSession
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
import java.util.Locale

private const val MEMBER_NUMBER_FORMAT = "GA%06d"

fun Route.myAccountRoutes() {
    get("/my-account") { call.handleMyAccountLoad() }
    post("/my-account/name") { call.handleMyAccountNameUpdate() }
    get("/account/fare-summary") { call.handleFareSummaryFragment() }
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
                "layout" to "_layout/base.peb",
            ),
        )
    }
}

private fun membershipNumberFor(userId: Int): String = String.format(Locale.UK, MEMBER_NUMBER_FORMAT, userId)

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
