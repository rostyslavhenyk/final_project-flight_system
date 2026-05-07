package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SortOrder

// chat messages table
object ChatMessages : Table("chat_messages") {
    private const val SENDER_NAME_LENGTH = 128
    private const val MESSAGE_LENGTH = 1000

    val id = integer("id").autoIncrement()
    val userId = integer("userId")
    val senderName = varchar("senderName", SENDER_NAME_LENGTH)
    val message = varchar("message", MESSAGE_LENGTH)
    val isStaff = bool("isStaff").default(false)
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

object ChatConversationStates : Table("chat_conversation_states") {
    val userId = integer("userId")
    val isClosed = bool("isClosed").default(false)
    val closedAt = long("closedAt").nullable()

    override val primaryKey = PrimaryKey(userId)
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
    fun add(
        userId: Int,
        senderName: String,
        message: String,
        isStaff: Boolean,
    ): ChatMessage =
        transaction {
            if (!isStaff) {
                reopen(userId)
            }
            val timestamp = System.currentTimeMillis()
            val id =
                ChatMessages.insert {
                    it[ChatMessages.userId] = userId
                    it[ChatMessages.senderName] = senderName
                    it[ChatMessages.message] = message
                    it[ChatMessages.isStaff] = isStaff
                    it[ChatMessages.timestamp] = timestamp
                } get ChatMessages.id

            ChatMessage(id, userId, senderName, message, isStaff, timestamp)
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

    fun getAllOpen(): List<ChatMessage> =
        transaction {
            val closedUsers =
                ChatConversationStates
                    .select(ChatConversationStates.userId)
                    .where { ChatConversationStates.isClosed eq true }
                    .map { it[ChatConversationStates.userId] }
                    .toSet()

            ChatMessages
                .selectAll()
                .orderBy(ChatMessages.timestamp, SortOrder.ASC)
                .map { it.toChatMessage() }
                .filterNot { it.userId in closedUsers }
        }

    fun close(userId: Int): Boolean =
        transaction {
            val hasMessages =
                ChatMessages
                    .select(ChatMessages.id)
                    .where { ChatMessages.userId eq userId }
                    .limit(1)
                    .any()
            if (!hasMessages) return@transaction false

            ChatConversationStates.deleteWhere { ChatConversationStates.userId eq userId }
            ChatConversationStates.insert {
                it[ChatConversationStates.userId] = userId
                it[isClosed] = true
                it[closedAt] = System.currentTimeMillis()
            }
            true
        }

    fun isClosed(userId: Int): Boolean =
        transaction {
            ChatConversationStates
                .select(ChatConversationStates.userId)
                .where { (ChatConversationStates.userId eq userId) and (ChatConversationStates.isClosed eq true) }
                .any()
        }

    private fun reopen(userId: Int) {
        ChatConversationStates.deleteWhere { ChatConversationStates.userId eq userId }
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
