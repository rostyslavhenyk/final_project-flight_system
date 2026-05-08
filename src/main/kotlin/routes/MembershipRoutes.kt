package routes

import auth.UserSession
import data.LoyaltyUserRepository
import data.UserRepository
import io.ktor.server.application.*
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import utils.jsMode
import utils.timed

fun Route.membershipRoutes() {
    get("/membership") { call.handleMembershipList() }
}

private suspend fun ApplicationCall.handleMembershipList() {
    timed("T0_membership_list", jsMode()) {
        val session = sessions.get<UserSession>()
        val user = session?.let { UserRepository.get(it.id) }
        if (user != null && user.roleId in setOf(1, 2)) {
            LoyaltyUserRepository.delete(user.id)
            respondRedirect("/staff")
            return@timed
        }
        renderTemplate(
            "user/membership/index.peb",
            mapOf(
                "title" to "Membership",
                "account" to user?.let { UserRepository.getFull(it.id) },
            ),
        )
    }
}
