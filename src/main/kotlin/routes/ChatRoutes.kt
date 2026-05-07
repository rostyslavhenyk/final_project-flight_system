package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import auth.UserSession
import data.ChatRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.jsMode
import utils.timed

// customer chat routes
fun Route.chatRoutes() {
    post("/chat/send") { call.handleSendMessage() }
    get("/chat/messages") { call.handleGetMessages() }
}

@Serializable
data class ChatMessageResponse(
    val id: Int,
    val senderName: String,
    val message: String,
    val isStaff: Boolean,
    val timestamp: Long,
)

private suspend fun ApplicationCall.handleSendMessage() {
    timed("T2_chat_send", jsMode()) {
        val session = sessions.get<UserSession>()

        if (session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val params = receiveParameters()
        val message = params["message"]

        if (message.isNullOrBlank()) {
            respond(HttpStatusCode.BadRequest)
            return@timed
        }

        ChatRepository.add(session.id, session.firstname, message, false)
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleGetMessages() {
    timed("T2_chat_messages", jsMode()) {
        val session = sessions.get<UserSession>()

        if (session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val messages = ChatRepository.getByUser(session.id)
        val response =
            messages.map {
                ChatMessageResponse(it.id, it.senderName, it.message, it.isStaff, it.timestamp)
            }

        respondText(Json.encodeToString(response), ContentType.Application.Json)
    }
}
