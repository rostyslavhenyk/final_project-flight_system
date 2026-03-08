package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_about_us", jsMode()) {
        val pebble = getEngine()

        val model =
            mapOf(
                "title" to "Help",
            )

        val template = pebble.getTemplate("help/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
