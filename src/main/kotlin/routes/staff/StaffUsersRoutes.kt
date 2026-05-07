package routes.staff

import data.UserRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.renderTemplate
import utils.jsMode
import utils.timed

fun Route.staffUsersRoutes() {
    get("/users") { call.handleStaffUsers() }
}

private suspend fun ApplicationCall.handleStaffUsers() {
    timed("T4_staff_users_list", jsMode()) {
        renderTemplate(
            "staff/users/index.peb",
            mapOf(
                "title" to "Staff Users",
                "users" to UserRepository.allFull(),
            ),
        )
    }
}
