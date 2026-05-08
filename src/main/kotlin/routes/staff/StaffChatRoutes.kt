package routes.staff

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import auth.UserSession
import data.ChatRepository
import data.ChatMessage
import data.UserRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import routes.pebbleEngine
import utils.baseModel
import utils.jsMode
import utils.timed
import java.io.StringWriter

fun Route.staffChatRoutes() {
    get("/chat") { call.handleStaffChatLoad() }
    get("/chat/conversations") { call.handleStaffChatConversations() }
    get("/chat/messages") { call.handleStaffChatMessages() }
    get("/chat/summary") { call.handleStaffChatSummary() }
    post("/chat/reply") { call.handleStaffReply() }
    post("/chat/close") { call.handleStaffChatClose() }
}

private suspend fun ApplicationCall.handleStaffChatLoad() {
    timed("T4_staff_chat_load", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Staff Chat",
                    "conversations" to staffChatConversations(),
                    "openChatUserId" to (request.queryParameters["openUserId"]?.toIntOrNull() ?: 0),
                ),
            )

        val template = pebbleEngine.getTemplate("staff/chat/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleStaffChatConversations() {
    timed("T4_staff_chat_conversations", jsMode()) {
        respondText(Json.encodeToString(staffChatSummaries()), ContentType.Application.Json)
    }
}

private suspend fun ApplicationCall.handleStaffChatMessages() {
    timed("T4_staff_chat_messages", jsMode()) {
        val userId = request.queryParameters["userId"]?.toIntOrNull()
        val conversation =
            if (userId == null || ChatRepository.isClosed(userId)) {
                null
            } else {
                conversationResponse(userId, ChatRepository.getByUser(userId))
            }

        if (conversation == null) {
            respond(HttpStatusCode.NotFound)
            return@timed
        }

        respondText(Json.encodeToString(conversation), ContentType.Application.Json)
    }
}

private suspend fun ApplicationCall.handleStaffChatSummary() {
    timed("T4_staff_chat_summary", jsMode()) {
        respondText(
            Json.encodeToString(StaffChatSummaryResponse(ChatRepository.staffUnreadConversationCount())),
            ContentType.Application.Json,
        )
    }
}

private suspend fun ApplicationCall.handleStaffReply() {
    timed("T4_staff_chat_reply", jsMode()) {
        val session = sessions.get<UserSession>()

        if (session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val params = receiveParameters()
        val userId = params["userId"]?.toIntOrNull()
        val message = params["message"]

        if (userId == null || message.isNullOrBlank()) {
            respond(HttpStatusCode.BadRequest)
            return@timed
        }

        ChatRepository.add(userId, "Support Team", message, true)
        respondRedirect("/staff/chat?openUserId=$userId")
    }
}

private suspend fun ApplicationCall.handleStaffChatClose() {
    timed("T4_staff_chat_close", jsMode()) {
        val session = sessions.get<UserSession>()

        if (session == null) {
            respond(HttpStatusCode.Unauthorized)
            return@timed
        }

        val userId = receiveParameters()["userId"]?.toIntOrNull()

        if (userId == null || !ChatRepository.close(userId)) {
            respond(HttpStatusCode.BadRequest)
            return@timed
        }

        respondRedirect("/staff/chat")
    }
}

private fun data.User?.displayName(): String =
    this
        ?.let { "${it.firstname} ${it.lastname}".trim() }
        ?.takeIf { it.isNotBlank() }
        ?: "Unknown customer"

private fun staffChatConversations(): List<StaffChatConversationResponse> =
    ChatRepository
        .getAllOpen()
        .groupBy { it.userId }
        .map { (userId, messages) -> conversationResponse(userId, messages) }
        .sortedByDescending { it.lastMessageAt }

private fun staffChatSummaries(): List<StaffChatSummaryItemResponse> =
    staffChatConversations().map { conversation ->
        StaffChatSummaryItemResponse(
            userId = conversation.userId,
            userName = conversation.userName,
            userEmail = conversation.userEmail,
            messageCount = conversation.messageCount,
            lastMessageAt = conversation.lastMessageAt,
            unread = conversation.unread,
            lastMessage = conversation.lastMessage,
        )
    }

private fun conversationResponse(
    userId: Int,
    messages: List<ChatMessage>,
): StaffChatConversationResponse {
    val user = UserRepository.get(userId)
    val lastMessageAt = messages.maxOfOrNull { it.timestamp } ?: 0L

    return StaffChatConversationResponse(
        userId = userId,
        userName = user.displayName(),
        userEmail = user?.email ?: "Unknown account",
        messageCount = messages.size,
        lastMessageAt = lastMessageAt,
        unread = messages.maxByOrNull { it.timestamp }?.isStaff == false,
        lastMessage = messages.lastOrNull()?.message.orEmpty(),
        messages = messages.map { it.toStaffChatMessageResponse() },
    )
}

private fun ChatMessage.toStaffChatMessageResponse(): StaffChatMessageResponse =
    StaffChatMessageResponse(
        id = id,
        senderName = senderName,
        message = message,
        isStaff = isStaff,
        timestamp = timestamp,
    )

@Serializable
private data class StaffChatConversationResponse(
    val userId: Int,
    val userName: String,
    val userEmail: String,
    val messageCount: Int,
    val lastMessageAt: Long,
    val unread: Boolean,
    val lastMessage: String,
    val messages: List<StaffChatMessageResponse>,
)

@Serializable
private data class StaffChatSummaryItemResponse(
    val userId: Int,
    val userName: String,
    val userEmail: String,
    val messageCount: Int,
    val lastMessageAt: Long,
    val unread: Boolean,
    val lastMessage: String,
)

@Serializable
private data class StaffChatMessageResponse(
    val id: Int,
    val senderName: String,
    val message: String,
    val isStaff: Boolean,
    val timestamp: Long,
)

@Serializable
private data class StaffChatSummaryResponse(
    val unreadConversations: Int,
)
