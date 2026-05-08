package routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import utils.jsMode
import utils.timed

fun Route.membershipRoutes() {
    get("/membership") { handleMembershipList(call) }
    get("/membership/benefits") { handleMembershipBenefitsList(call) }
}

private suspend fun handleMembershipList(call: ApplicationCall) {
    call.timed("T0_membership_list", call.jsMode()) {
        call.renderTemplate("user/membership/index.peb", mapOf("title" to "Membership"))
    }
}
private suspend fun handleMembershipBenefitsList(call: ApplicationCall) {
    call.timed("T0_membership_benefits_list", call.jsMode()) {
        call.renderTemplate("user/membership/benefits.peb", mapOf("title" to "Membership"))
    }
}
