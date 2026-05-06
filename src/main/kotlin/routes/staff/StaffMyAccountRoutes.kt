package routes.staff

import auth.UserSession
import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import routes.renderTemplate
import utils.jsMode
import utils.timed

fun Route.staffMyAccountRoutes() {
    get("/my-account") { call.handleStaffMyAccountLoad() }
}

private suspend fun ApplicationCall.handleStaffMyAccountLoad() {
    timed("T4_staff_account_load", jsMode()) {
        val session = sessions.get<UserSession>()

        if (session == null) {
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        val account = UserRepository.get(session.id)

        if (account == null) {
            sessions.clear<UserSession>()
            respond(HttpStatusCode.NotFound, "Page not found")
            return@timed
        }

        renderTemplate(
            "staff/my-account/index.peb",
            mapOf(
                "title" to "My Account",
                "account" to account,
            ),
        )
    }
}
