package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed
import auth.LoggedInState
import utils.baseModel

fun Route.myAccountRoutes() {
    get("/my-account") { call.handleMyAccountLoad() }
}

private suspend fun ApplicationCall.handleMyAccountLoad() {
    timed("T0_my_account", jsMode()) {
        val loggedState: LoggedInState = loggedIn()
        if (!loggedState.loggedIn) {
            return@timed respond(HttpStatusCode.NotFound, "Page not found")
        }

        val userId = loggedState.session?.id ?: -1
        val pebble = getEngine()
        val model =
            baseModel(
                mapOf("title" to "My Account"),
            )

        val template = pebble.getTemplate("my-account/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
