package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import utils.jsMode
import utils.timed
import auth.UserSession
import data.UserRepository
import utils.baseModel

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

        val model =
            baseModel(
                mapOf(
                    "title" to "My Account",
                    "account" to account,
                    "layout" to if (account.roleId == 1) "_layout/basestaff.peb" else "_layout/base.peb",
                ),
            )

        val template = pebbleEngine.getTemplate("user/my-account/index.peb")
        val writer = StringWriter()

        template.evaluate(writer, model)

        respondText(writer.toString(), ContentType.Text.Html)
    }
}
