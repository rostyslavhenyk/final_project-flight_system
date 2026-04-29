package utils

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import auth.UserSession
import data.UserRepository

fun ApplicationCall.baseModel(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
    val metricsSession = sessions.get<SessionUtils>()
    val userSession = sessions.get<UserSession>()

    val user =
        userSession?.let {
            UserRepository.get(it.id)
        }

    if (user == null && userSession != null) {
        sessions.clear<UserSession>()
    }

    return extra +
        mapOf(
            "sessionId" to (metricsSession?.id ?: "anonymous"),
            "loggedIn" to (user != null),
            "user" to user,
            "isStaff" to (user?.roleId == 1),
        )
}
