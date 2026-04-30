package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.membershipRoutes() {
    get("/membership") { call.handleMembershipList() }
}

private suspend fun ApplicationCall.handleMembershipList() {
    timed("T0_membership_list", jsMode()) {
        val model =
            baseModel(
                mapOf("title" to "Membership"),
            )

        val template = pebbleEngine.getTemplate("user/membership/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
