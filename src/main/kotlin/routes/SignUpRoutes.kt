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

fun Route.signUpRoutes() {
    get("/signup") { call.handleSignUpLoad() }
    post("/signup") { call.handleSignUpPost() }
}

fun ApplicationCall.createSignUpStatus(message: String): String =
    """<div id="sign-up-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleSignUpLoad() {
    timed("T0_sign_up", jsMode()) {
        val pebble = getEngine()
        val logged_state: LoggedInState = loggedIn()

        if (logged_state.logged_in) {
            respondRedirect("/")
        }

        val model =
            mapOf(
                "title" to "Sign Up",
            )

        val template = pebble.getTemplate("sign-up/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleSignUpPost() {
    timed("T1_sign_up_post", jsMode()) {
        val pebble = getEngine()
        val params = receiveParameters()

        val firstname = params["firstname"]
        val lastname = params["lastname"]
        val email = params["email"]
        val password = params["password"]

        if (firstname.isNullOrBlank()) {
            respondText(
                createSignUpStatus("Please fill in a first name"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (lastname.isNullOrBlank()) {
            respondText(
                createSignUpStatus("Please fill in a last name"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (email.isNullOrBlank()) {
            respondText(
                createSignUpStatus("Please fill in an email"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (password.isNullOrBlank()) {
            respondText(
                createSignUpStatus("Please fill in a password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val usr: User = UserRepository.getByEmail(email.toString())

        if (usr.id != -1) {
            respondText(
                createSignUpStatus("User already exists with that email"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val u = UserRepository.add(firstname, lastname, 0, email, password)
        sessions.set(UserSession(u.id, firstname))
        response.headers.append("HX-Redirect", "/")
        respond(HttpStatusCode.OK)
    }
}
