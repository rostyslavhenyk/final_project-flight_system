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

// login and logout routes
fun Route.logInRoutes() {
    get("/login") { call.handleLogInLoad() }
    post("/login") { call.handleLogInPost() }
    get("/logout") { call.handleLogOut() }
}

fun ApplicationCall.createLoginStatus(message: String): String =
    """<div id="log-in-status" class="auth-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleLogInLoad() {
    timed("T1_login_load", jsMode()) {
        val redirectUrl = safeLoginRedirect(request.queryParameters["redirect"])
        if (sessions.get<UserSession>() != null) {
            respondRedirect(redirectUrl)
            return@timed
        }

        renderTemplate("user/log-in/index.peb", mapOf("title" to "Log In", "redirect" to redirectUrl))
    }
}

// checks email and password then logs user in
private suspend fun ApplicationCall.handleLogInPost() {
    timed("T1_login_submit", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]
        val password = params["password"]
        val redirectUrl = safeLoginRedirect(params["redirect"])

        if (email == null || password == null) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val usr = UserRepository.getByEmail(email)

        if (usr == null) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (!BCrypt.checkpw(password, usr.password)) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        sessions.set(UserSession(usr.id, usr.firstname))
        val redirectTarget = if (usr.roleId == 1 || usr.roleId == 2) "/staff" else redirectUrl
        response.headers.append("HX-Redirect", redirectTarget)
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleLogOut() {
    timed("T1_logout", jsMode()) {
        sessions.clear<UserSession>()
        respondRedirect("/")
    }
}

private fun safeLoginRedirect(rawRedirect: String?): String {
    val redirect = rawRedirect?.trim().orEmpty()
    val unsafeRedirect =
        redirect.isBlank() ||
            !redirect.startsWith("/") ||
            redirect.startsWith("//") ||
            redirect.contains('\r') ||
            redirect.contains('\n') ||
            redirect == "/login" ||
            redirect.startsWith("/login?")
    return if (unsafeRedirect) "/" else redirect
}
