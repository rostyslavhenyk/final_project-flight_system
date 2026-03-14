package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.commitmentRoutes() {
    get("/commitment") { call.handleCommitmentLoad() }
}

private suspend fun ApplicationCall.handleCommitmentLoad() {
    timed("T0_commitment", jsMode()) {
        val pebble = getEngine()
        val model = mapOf("title" to "Our commitment to you")
        val template = pebble.getTemplate("commitment/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
