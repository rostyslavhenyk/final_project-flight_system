package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.StringWriter
import auth.*

fun ApplicationCall.isHtmx(): Boolean = request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true

fun ApplicationCall.getEngine(): PebbleEngine =
    PebbleEngine
        .Builder()
        .loader(
            io.pebbletemplates.pebble.loader.ClasspathLoader().apply {
                prefix = "templates/"
            },
        ).build()

fun ApplicationCall.loggedIn(): LoggedInState {
    val user = sessions.get("USER_SESSION") as UserSession?
    return LoggedInState(user != null, user)
}

fun ApplicationCall.loggedMap(): Map<String, Any?> {
    val logged_state = loggedIn()
    return mapOf(
        "logged_in" to logged_state.logged_in,
        "user" to logged_state.session,
    )
}

fun ApplicationCall.fullEvaluate(
    template: PebbleTemplate,
    writer: StringWriter,
    model: Map<String, Any?>,
) {
    template.evaluate(writer, model + loggedMap())
}
