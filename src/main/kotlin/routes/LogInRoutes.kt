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
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// login and logout routes
fun Route.logInRoutes() {
    get("/login") { call.handleLogInLoad() }
    post("/login") { call.handleLogInPost() }
    post("/logout") { call.handleLogOut() }
    get("/logout") { call.respondRedirect("/") }
}

fun ApplicationCall.createLoginStatus(message: String): String =
    """
    <div id="log-in-status" class="auth-status" hx-swap-oob="true" role="status"
         aria-live="polite" aria-atomic="true">$message</div>
    """.trimIndent()

private suspend fun ApplicationCall.handleLogInLoad() {
    timed("T1_login_load", jsMode()) {
        val redirectUrl = loginRedirectTarget()
        if (sessions.get<UserSession>() != null) {
            respondRedirect(redirectUrl)
            return@timed
        }

        renderTemplate(
            "user/log-in/index.peb",
            mapOf(
                "title" to "Log In",
                "redirect" to redirectUrl,
                "redirectParam" to URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8),
            ),
        )
    }
}

// checks email and password then logs user in
private suspend fun ApplicationCall.handleLogInPost() {
    timed("T1_login_submit", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]
        val password = params["password"]
        val redirectUrl = safeLoginRedirectParam(params)

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
        if (request.headers["HX-Request"] == "true") {
            response.headers.append("HX-Redirect", redirectTarget)
            respond(HttpStatusCode.OK)
        } else {
            respondRedirect(redirectTarget)
        }
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

private fun safeLoginRedirectParam(params: Parameters): String {
    val rawRedirect = params["redirect"] ?: params["returnUrl"]
    return safeLoginRedirect(rawRedirect)
}

private fun Parameters.hasLoginRedirect(): Boolean = contains("redirect") || contains("returnUrl")

private fun ApplicationCall.loginRedirectTarget(): String {
    if (request.queryParameters.hasLoginRedirect()) {
        return safeLoginRedirectParam(request.queryParameters)
    }
    return safeLoginRedirect(refererPath())
}

private fun ApplicationCall.refererPath(): String? {
    val referer = request.headers[HttpHeaders.Referrer]?.trim().orEmpty()
    if (referer.isBlank()) return null

    return runCatching {
        val uri = URI(referer)
        if (uri.isAbsolute && !uri.host.equals(request.host(), ignoreCase = true)) {
            return@runCatching null
        }
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        "$path$query$fragment"
    }.getOrNull()
}
