package routes

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import auth.UserSession
import auth.LoggedInState

val pebbleEngine: PebbleEngine =
    PebbleEngine
        .Builder()
        .loader(
            ClasspathLoader().apply {
                prefix = "templates/"
            },
        ).build()

fun ApplicationCall.loggedIn(): LoggedInState {
    val user = sessions.get<UserSession>()
    return LoggedInState(user != null, user)
}
