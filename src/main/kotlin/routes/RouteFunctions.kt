package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.StringWriter
import auth.UserSession
import auth.LoggedInState

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
    val user = sessions.get<UserSession>()
    return LoggedInState(user != null, user)
}

fun ApplicationCall.fullEvaluate(
    template: PebbleTemplate,
    writer: StringWriter,
    model: Map<String, Any?>,
) {
    template.evaluate(writer, model)
}
