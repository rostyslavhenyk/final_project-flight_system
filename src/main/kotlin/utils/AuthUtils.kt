package utils

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import auth.UserSession
import data.User
import data.UserRepository

fun ApplicationCall.getUser(): User? {
    val session = sessions.get<UserSession>() ?: return null
    return UserRepository.get(session.id)
}

fun ApplicationCall.isStaff(): Boolean = getUser()?.roleId == 1
