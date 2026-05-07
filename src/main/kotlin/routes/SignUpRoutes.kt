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
import auth.UserSession
import utils.baseModel
import org.mindrot.jbcrypt.BCrypt

// signup routes
fun Route.signUpRoutes() {
    get("/signup") { call.handleSignUpLoad() }
    post("/signup") { call.handleSignUpPost() }
}

fun ApplicationCall.createSignUpStatus(message: String): String =
    """<div id="sign-up-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleSignUpLoad() {
    timed("T0_sign_up", jsMode()) {
        if (sessions.get<UserSession>() != null) {
            respondRedirect("/")
            return@timed
        }

        val model = baseModel(mapOf("title" to "Sign Up"))
        val template = pebbleEngine.getTemplate("user/sign-up/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

// validates fields then creates the user
private suspend fun ApplicationCall.handleSignUpPost() {
    timed("T1_sign_up_post", jsMode()) {
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
            val u = UserRepository.add(firstname, lastname, 0, email, hashedPassword)
            sessions.set(UserSession(u.id, firstname))
            response.headers.append("HX-Redirect", "/")
            respond(HttpStatusCode.OK)
        }
    }
