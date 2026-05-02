package routes.staff

import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.pebbleEngine
import utils.baseModel
import utils.jsMode
import utils.timed
import java.io.StringWriter

fun Route.staffUsersRoutes() {
    get("/users") { call.handleStaffUsers() }
}

private suspend fun ApplicationCall.handleStaffUsers() {
    timed("T4_staff_users_list", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Staff Users",
                    "users" to UserRepository.allFull(),
                ),
            )

        val template = pebbleEngine.getTemplate("staff/users/index.peb")
        val writer = StringWriter()

        template.evaluate(writer, model)

        respondText(writer.toString(), ContentType.Text.Html)
    }
}
