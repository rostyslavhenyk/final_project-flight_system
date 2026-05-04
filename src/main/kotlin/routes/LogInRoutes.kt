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

// login and logout routes
fun Route.logInRoutes() {
    get("/login") { call.handleLogInLoad() }
    post("/login") { call.handleLogInPost() }
    get("/logout") { call.handleLogOut() }
}

fun ApplicationCall.createLoginStatus(message: String): String =
    """<div id="log-in-status" hx-swap-oob="true" role="status" aria-live="polite" aria-atomic="true">$message</div>"""

private suspend fun ApplicationCall.handleLogInLoad() {
    timed("T0_log_in", jsMode()) {
        if (sessions.get<UserSession>() != null) {
            respondRedirect("/")
            return@timed
        }

        val model = baseModel(mapOf("title" to "Log In"))
        val template = pebbleEngine.getTemplate("user/log-in/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

// checks email and password then logs user in
private suspend fun ApplicationCall.handleLogInPost() {
    timed("T1_log_in_post", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]
        val password = params["password"]

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
        val redirectUrl = if (usr.roleId == 1) "/staff" else "/"
        response.headers.append("HX-Redirect", redirectUrl)
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleLogOut() {
    timed("T2_log_out", jsMode()) {
        sessions.clear<UserSession>()
        respondRedirect("/")
    }
}
