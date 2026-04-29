package routes.staff

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.isStaff

fun Route.staffRoutes() {
    route("/staff") {
        intercept(ApplicationCallPipeline.Plugins) {
            if (!call.isStaff()) {
                call.respondRedirect("/")
                finish()
            }
        }

        staffDashboardRoutes()
        staffFlightsRoutes()
        staffUsersRoutes()
    }
}
