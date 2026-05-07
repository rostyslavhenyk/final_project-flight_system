package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import utils.jsMode
import utils.timed
import data.UserRepository
import auth.UserSession
import org.mindrot.jbcrypt.BCrypt

// signup routes
fun Route.signUpRoutes() {
    get("/signup") { call.handleSignUpLoad() }
    post("/signup") { call.handleSignUpPost() }
}

fun createSignUpStatus(message: String): String =
    """<div id="sign-up-status" class="auth-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleSignUpLoad() {
    timed("T1_signup_load", jsMode()) {
        if (sessions.get<UserSession>() != null) {
            respondRedirect("/")
            return@timed
        }

        renderTemplate("user/sign-up/index.peb", mapOf("title" to "Sign Up"))
    }
}

// validates fields then creates the user
private suspend fun ApplicationCall.handleSignUpPost() {
    timed("T1_signup_submit", jsMode()) {
        val params = receiveParameters()

        val firstname = params["firstname"]
        val lastname = params["lastname"]
        val email = params["email"]?.trim()
        val phone = params["phone"]?.trim().orEmpty()
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

        // password requirements check
        if (password.length < 10) {
            respondText(
                createSignUpStatus("Password must be at least 10 characters"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (!password.any { it.isUpperCase() }) {
            respondText(
                createSignUpStatus("Password must contain at least one capital letter"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val existingUser = UserRepository.getByEmail(email)

        if (existingUser != null) {
            respondText(
                createSignUpStatus("User already exists with that email"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val createdUser = UserRepository.add(firstname, lastname, 0, email, hashedPassword, phone)
        sessions.set(UserSession(createdUser.id, createdUser.firstname))
        response.headers.append("HX-Redirect", "/")
        respond(HttpStatusCode.OK)
    }
}
