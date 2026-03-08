package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import utils.jsMode
import utils.timed
import data.UserRepository
import data.User
import auth.UserSession
import auth.LoggedInState

fun Route.logInRoutes() {
    get("/login") { call.handleLogInLoad() }
    post("/login") { call.handleLogInPost() }
    get("/logout") { call.handleLogOut() }
}

fun ApplicationCall.createLoginStatus(message: String): String =
    """<div id="log-in-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleLogInLoad() {
    timed("T0_log_in", jsMode()) {
        val pebble = getEngine()
        val logged_state: LoggedInState = loggedIn()

        if (logged_state.logged_in) {
            respondRedirect("/")
        }

        val model =
            mapOf(
                "title" to "Log In",
            )

        val template = pebble.getTemplate("log-in/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleLogInPost() {
    timed("T1_log_in_post", jsMode()) {
        val pebble = getEngine()
        val params = receiveParameters()
        val email = params["email"]
        val password = params["password"]

        if (email == null) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val usr: User = UserRepository.getByEmail(email.toString())

        if (usr.id == -1) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (usr.password != password) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        sessions.set(UserSession(usr.id, usr.firstname))
        response.headers.append("HX-Redirect", "/")
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleLogOut() {
    timed("T2_log_out", jsMode()) {
        sessions.clear<UserSession>()
        respondRedirect("/")
    }
}
