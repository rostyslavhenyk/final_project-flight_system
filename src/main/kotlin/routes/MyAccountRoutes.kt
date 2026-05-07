package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import utils.jsMode
import utils.timed
import auth.UserSession
import data.UserRepository

fun Route.myAccountRoutes() {
    get("/my-account") { call.handleMyAccountLoad() }
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
                "layout" to "_layout/base.peb",
            ),
        )
    }
}
