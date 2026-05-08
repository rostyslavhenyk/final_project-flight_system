package routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import utils.jsMode
import utils.timed

fun Route.membershipRoutes() {
    get("/membership") { call.handleMembershipList() }
    get("/membership/benefits") { call.handleMembershipBenefitsList() }
}

private suspend fun ApplicationCall.handleMembershipList() {
    timed("T0_membership_list", jsMode()) {
        renderTemplate("user/membership/index.peb", mapOf("title" to "Membership"))
    }
}
private suspend fun ApplicationCall.handleMembershipBenefitsList() {
    timed("T0_membership_benefits_list", jsMode()) {
        renderTemplate("user/membership/benefits.peb", mapOf("title" to "Membership"))
    }
}
