package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed
import auth.LoggedInState

fun Route.myAccountRoutes() {
    get("/my-account") { call.handleMyAccountLoad() }
}

private suspend fun ApplicationCall.handleMyAccountLoad() {
    timed("T0_my_account", jsMode()) {
        val logged_state: LoggedInState = loggedIn()
        if (!logged_state.logged_in) {
            return@timed respond(HttpStatusCode.NotFound, "Page not found")
        }

        val userId = logged_state.session?.id ?: -1
        val pebble = getEngine()
        val model =
            mapOf(
                "title" to "My Account",
            )

        val template = pebble.getTemplate("my-account/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
