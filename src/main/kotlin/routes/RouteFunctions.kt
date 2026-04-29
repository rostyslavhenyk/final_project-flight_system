package routes

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import auth.UserSession
import auth.LoggedInState

fun ApplicationCall.isHtmx(): Boolean = request.headers["HX-Request"]?.equals("true", ignoreCase = true) == true

val pebbleEngine: PebbleEngine =
    PebbleEngine
        .Builder()
        .loader(
            ClasspathLoader().apply {
                prefix = "templates/"
            },
        ).build()

fun ApplicationCall.getEngine(): PebbleEngine = pebbleEngine

fun ApplicationCall.loggedIn(): LoggedInState {
    val user = sessions.get<UserSession>()
    return LoggedInState(user != null, user)
}
