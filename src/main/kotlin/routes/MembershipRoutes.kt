package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.membershipRoutes() {
    get("/membership") { call.handleMembershipList() }
}

private suspend fun ApplicationCall.handleMembershipList() {
    timed("T0_membership_list", jsMode()) {
        val pebble = getEngine()

        val model =
            mapOf(
                "title" to "Membership",
            )

        val template = pebble.getTemplate("membership/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
