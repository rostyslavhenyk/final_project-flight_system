package routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import utils.jsMode
import utils.timed

fun Route.commitmentRoutes() {
    get("/commitment") { call.handleCommitmentLoad() }
}

private suspend fun ApplicationCall.handleCommitmentLoad() {
    timed("T0_commitment", jsMode()) {
        renderTemplate("user/commitment/index.peb", mapOf("title" to "Our commitment to you"))
    }
}
