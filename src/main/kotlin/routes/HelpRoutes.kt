package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_about_us", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Help",
                ),
            )
        val template = pebbleEngine.getTemplate("user/help/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
