package routes

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import io.pebbletemplates.pebble.template.PebbleTemplate
import auth.UserSession
import auth.LoggedInState
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import utils.baseModel
import java.io.StringWriter

val pebbleEngine: PebbleEngine =
    PebbleEngine
        .Builder()
        .loader(
            ClasspathLoader().apply {
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
    template.evaluate(writer, baseModel(model))
}

suspend fun ApplicationCall.renderTemplate(
    templatePath: String,
    model: Map<String, Any?> = emptyMap(),
) {
    val template = pebbleEngine.getTemplate(templatePath)
    val writer = StringWriter()
    fullEvaluate(template, writer, model)
    respondText(writer.toString(), ContentType.Text.Html)
}
