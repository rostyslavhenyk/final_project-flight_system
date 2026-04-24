package utils

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import auth.UserSession

fun ApplicationCall.baseModel(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
    val metricsSession = sessions.get<SessionUtils>()
    val userSession = sessions.get<UserSession>()

    return extra +
        mapOf(
            "sessionId" to (metricsSession?.id ?: "anonymous"),
            "loggedIn" to (userSession != null),
            "user" to userSession,
        )
}
