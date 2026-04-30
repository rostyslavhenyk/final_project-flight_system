package routes.staff

import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.pebbleEngine
import utils.baseModel
import java.io.StringWriter

fun Route.staffUsersRoutes() {
    get("/users") { call.handleStaffUsers() }
}

private suspend fun ApplicationCall.handleStaffUsers() {
    val model =
        baseModel(
            mapOf(
                "title" to "Staff Users",
                "users" to UserRepository.all(),
            ),
        )

    val template = pebbleEngine.getTemplate("staff/users/index.peb")
    val writer = StringWriter()

    template.evaluate(writer, model)

    respondText(writer.toString(), ContentType.Text.Html)
}
