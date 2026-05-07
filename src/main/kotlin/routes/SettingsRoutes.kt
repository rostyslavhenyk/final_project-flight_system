package routes

import auth.UserSession
import data.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import utils.jsMode
import utils.timed

fun Route.settingsRoutes() {
    get("/settings") { call.handleSettingsPage() }
}

private suspend fun ApplicationCall.handleSettingsPage() {
    timed("T2_settings_load", jsMode()) {
        val loggedState = loggedIn()
        if (!loggedState.loggedIn || loggedState.session == null) {
            respondRedirect("/login?redirect=/settings")
            return@timed
        }

        val account = UserRepository.get(loggedState.session.id)
        if (account == null) {
            sessions.clear<UserSession>()
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        renderTemplate(
            "user/settings/index.peb",
            mapOf(
                "title" to "Settings",
                "account" to account,
                "layout" to "_layout/base.peb",
            ),
        )
    }
}
