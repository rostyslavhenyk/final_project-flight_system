package routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import utils.jsMode
import utils.timed

fun Route.legalRoutes() {
    get("/legal") { call.handleLegalLoad() }
}

private suspend fun ApplicationCall.handleLegalLoad() {
    timed("T0_legal", jsMode()) {
        renderTemplate("user/legal/index.peb", mapOf("title" to "Legal and Privacy"))
    }
}
