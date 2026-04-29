package routes.staff

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.pebbleEngine
import utils.baseModel
import java.io.StringWriter

fun Route.staffDashboardRoutes() {
    get { call.handleStaffDashboard() }
}

private suspend fun ApplicationCall.handleStaffDashboard() {
    val model =
        baseModel(
            mapOf("title" to "Staff Dashboard"),
        )

    val template = pebbleEngine.getTemplate("staff/dashboard/index.peb")
    val writer = StringWriter()

    template.evaluate(writer, model)

    respondText(writer.toString(), ContentType.Text.Html)
}
