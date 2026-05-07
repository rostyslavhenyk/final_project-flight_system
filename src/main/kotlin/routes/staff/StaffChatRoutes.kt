package routes.staff

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import auth.UserSession
import data.ChatRepository
import data.UserRepository
import routes.pebbleEngine
import utils.baseModel
import java.io.StringWriter

// staff chat routes
fun Route.staffChatRoutes() {
    get("/chat") { call.handleStaffChatLoad() }
    post("/chat/reply") { call.handleStaffReply() }
}

private suspend fun ApplicationCall.handleStaffChatLoad() {
    val allMessages = ChatRepository.getAll()

    val conversations =
        allMessages.groupBy { it.userId }.map { (userId, messages) ->
            val user = UserRepository.get(userId)
            mapOf(
                "userId" to userId,
                "userName" to user.displayName(),
                "userEmail" to (user?.email ?: "Unknown account"),
                "messageCount" to messages.size,
                "lastMessageAt" to (messages.maxOfOrNull { it.timestamp } ?: 0L),
                "messages" to
                    messages.map { msg ->
                        mapOf(
                            "id" to msg.id,
                            "senderName" to msg.senderName,
                            "message" to msg.message,
                            "isStaff" to msg.isStaff,
                            "timestamp" to msg.timestamp,
                        )
                    },
            )
        }.sortedByDescending { it["lastMessageAt"] as Long }

    val model =
        baseModel(
            mapOf(
                "title" to "Staff Chat",
                "conversations" to conversations,
            ),
        )

    val template = pebbleEngine.getTemplate("staff/chat/index.peb")
    val writer = StringWriter()
    template.evaluate(writer, model)
    respondText(writer.toString(), ContentType.Text.Html)
}

private suspend fun ApplicationCall.handleStaffReply() {
    val session = sessions.get<UserSession>()

    if (session == null) {
        respond(HttpStatusCode.Unauthorized)
        return
    }

    val params = receiveParameters()
    val userId = params["userId"]?.toIntOrNull()
    val message = params["message"]

    if (userId == null || message.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    ChatRepository.add(userId, "Support Team", message, true)
    respondRedirect("/staff/chat")
}

private fun data.User?.displayName(): String =
    this?.let { "${it.firstname} ${it.lastname}".trim() }
        ?.takeIf { it.isNotBlank() }
        ?: "Unknown customer"
