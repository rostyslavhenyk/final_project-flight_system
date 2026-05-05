package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder

// chat messages table
object ChatMessages : Table("chat_messages") {
    val id = integer("id").autoIncrement()
    val userId = integer("userId")
    val senderName = varchar("senderName", 128)
    val message = varchar("message", 1000)
    val isStaff = bool("isStaff").default(false)
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

data class ChatMessage(
    val id: Int,
    val userId: Int,
    val senderName: String,
    val message: String,
    val isStaff: Boolean,
    val timestamp: Long,
)

// handles database queries for chat messages
object ChatRepository {

    fun add(userId: Int, senderName: String, message: String, isStaff: Boolean): ChatMessage =
        transaction {
            val id = ChatMessages.insert {
                it[ChatMessages.userId] = userId
                it[ChatMessages.senderName] = senderName
                it[ChatMessages.message] = message
                it[ChatMessages.isStaff] = isStaff
                it[ChatMessages.timestamp] = System.currentTimeMillis()
            } get ChatMessages.id

            ChatMessage(id, userId, senderName, message, isStaff, System.currentTimeMillis())
        }

    fun getByUser(userId: Int): List<ChatMessage> =
        transaction {
            ChatMessages
                .selectAll()
                .where { ChatMessages.userId eq userId }
                .orderBy(ChatMessages.timestamp, SortOrder.ASC)
                .map { it.toChatMessage() }
        }

    fun getAll(): List<ChatMessage> =
        transaction {
            ChatMessages
                .selectAll()
                .orderBy(ChatMessages.timestamp, SortOrder.ASC)
                .map { it.toChatMessage() }
        }

    private fun ResultRow.toChatMessage(): ChatMessage =
        ChatMessage(
            id = this[ChatMessages.id],
            userId = this[ChatMessages.userId],
            senderName = this[ChatMessages.senderName],
            message = this[ChatMessages.message],
            isStaff = this[ChatMessages.isStaff],
            timestamp = this[ChatMessages.timestamp],
        )
}
