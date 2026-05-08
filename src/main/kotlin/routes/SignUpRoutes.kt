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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val MIN_SIGNUP_PASSWORD_LENGTH = 10
private val personNamePattern = Regex("^[\\p{L}][\\p{L}\\s'-]*$")

// signup routes
fun Route.signUpRoutes() {
    get("/signup") { call.handleSignUpLoad() }
    post("/signup") { call.handleSignUpPost() }
}

fun createSignUpStatus(message: String): String =
    """
    <div id="sign-up-status" class="auth-status" hx-swap-oob="true" role="status"
         aria-live="polite" aria-atomic="true">$message</div>
    """.trimIndent()

private suspend fun ApplicationCall.handleSignUpLoad() {
    timed("T1_signup_load", jsMode()) {
        val redirectUrl = signUpRedirectTarget(request.queryParameters)
        if (sessions.get<UserSession>() != null) {
            respondRedirect(redirectUrl)
            return@timed
        }

        renderTemplate(
            "user/sign-up/index.peb",
            mapOf(
                "title" to "Sign Up",
                "redirect" to redirectUrl,
                "redirectParam" to URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8),
            ),
        )
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
        val redirectUrl = signUpRedirectTarget(params)

        if (firstname.isNullOrBlank()) {
            respondSignUpStatus("Please fill in a first name")
            return@timed
        }

        if (lastname.isNullOrBlank()) {
            respondSignUpStatus("Please fill in a last name")
            return@timed
        }

        if (email.isNullOrBlank()) {
            respondSignUpStatus("Please fill in an email")
            return@timed
        }

        if (password.isNullOrBlank()) {
            respondSignUpStatus("Please fill in a password")
            return@timed
        }

        val nameFormatMessage = invalidNameMessage(firstname, lastname)
        if (nameFormatMessage != null) {
            respondSignUpStatus(nameFormatMessage)
            return@timed
        }

        if (respondPasswordRequirementFailure(password)) {
            return@timed
        }

        val existingUser = UserRepository.getByEmail(email)

        if (existingUser != null) {
            respondSignUpStatus("User already exists with that email")
            return@timed
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val createdUser = UserRepository.add(firstname, lastname, 0, email, hashedPassword, phone)
        sessions.set(UserSession(createdUser.id, createdUser.firstname))
        if (request.headers["HX-Request"] == "true") {
            response.headers.append("HX-Redirect", redirectUrl)
            respond(HttpStatusCode.OK)
        } else {
            respondRedirect(redirectUrl)
        }
    }
}

private suspend fun ApplicationCall.respondSignUpStatus(message: String) {
    respondText(
        createSignUpStatus(message),
        ContentType.Text.Html,
        status = HttpStatusCode.OK,
    )
}

private suspend fun ApplicationCall.respondPasswordRequirementFailure(password: String): Boolean {
    val message =
        when {
            password.length < MIN_SIGNUP_PASSWORD_LENGTH -> "Password must be at least 10 characters"
            !password.any { it.isUpperCase() } -> "Password must contain at least one capital letter"
            else -> return false
        }
    respondSignUpStatus(message)
    return true
}

private fun invalidNameMessage(
    firstname: String,
    lastname: String,
): String? =
    when {
        !firstname.trim().matches(personNamePattern) ->
            "First name can only include letters, spaces, hyphens and apostrophes"
        !lastname.trim().matches(personNamePattern) ->
            "Last name can only include letters, spaces, hyphens and apostrophes"
        else -> null
    }

private fun signUpRedirectTarget(params: Parameters): String {
    val redirect = params["redirect"]?.trim().orEmpty()
    val unsafeRedirect =
        redirect.isBlank() ||
            !redirect.startsWith("/") ||
            redirect.startsWith("//") ||
            redirect.contains('\r') ||
            redirect.contains('\n') ||
            redirect == "/signup" ||
            redirect.startsWith("/signup?") ||
            redirect == "/login" ||
            redirect.startsWith("/login?")
    return if (unsafeRedirect) "/" else redirect
}
