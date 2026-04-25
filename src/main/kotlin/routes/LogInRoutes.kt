package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.net.URLDecoder
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

/** Only same-origin relative paths (e.g. `/book/passengers?…`) — blocks open redirects. */
internal fun safeReturnUrlForLogin(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val decodedPath =
        try {
            URLDecoder.decode(raw.trim(), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            raw.trim()
        }
    if (!decodedPath.startsWith("/")) return null
    if (decodedPath.startsWith("//")) return null
    if (decodedPath.contains("://")) return null
    return decodedPath
}

private suspend fun ApplicationCall.handleLogInLoad() {
    timed("T0_log_in", jsMode()) {
        val pebble = getEngine()
        val loggedInState: LoggedInState = loggedIn()
        val returnRaw = request.queryParameters["returnUrl"]
        val safeReturn = safeReturnUrlForLogin(returnRaw)

        if (loggedInState.logged_in) {
            respondRedirect(safeReturn ?: "/")
            return@timed
        }

        val model =
            mapOf(
                "title" to "Log In",
                "returnUrl" to (returnRaw ?: ""),
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

        val userFromDb: User = UserRepository.getByEmail(email.toString())

        if (userFromDb.id == -1) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (userFromDb.password != password) {
            respondText(
                createLoginStatus("Incorrect email or password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        sessions.set(UserSession(userFromDb.id, userFromDb.firstname))
        val returnAfterLogin =
            safeReturnUrlForLogin(params["returnUrl"]?.toString())
                ?: "/"
        response.headers.append("HX-Redirect", returnAfterLogin)
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleLogOut() {
    timed("T2_log_out", jsMode()) {
        sessions.clear<UserSession>()
        respondRedirect("/")
    }
}
