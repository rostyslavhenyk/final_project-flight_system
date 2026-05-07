package routes.staff

import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.renderTemplate
import utils.jsMode
import utils.timed

fun Route.staffDashboardRoutes() {
    get { call.handleStaffDashboard() }
}

private suspend fun ApplicationCall.handleStaffDashboard() {
    timed("T4_staff_dashboard_load", jsMode()) {
        renderTemplate("staff/dashboard/index.peb", mapOf("title" to "Staff Dashboard"))
    }
}
