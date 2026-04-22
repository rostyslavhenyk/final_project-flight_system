package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.legalRoutes() {
    get("/legal") { call.handleLegalLoad() }
}

private suspend fun ApplicationCall.handleLegalLoad() {
    timed("T0_legal", jsMode()) {
        val pebble = getEngine()
        val model = mapOf("title" to "Legal and Privacies")
        val template = pebble.getTemplate("legal/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
