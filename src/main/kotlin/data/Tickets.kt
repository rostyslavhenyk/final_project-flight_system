package data

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Tickets : Table("tickets") {
    private const val SUBJECT_LENGTH = 160
    private const val MESSAGE_LENGTH = 2000
    private const val STATUS_LENGTH = 24
    private const val PRIORITY_LENGTH = 24
    private const val SOURCE_LENGTH = 16

    val id = integer("id").autoIncrement()
    val userID = integer("userID").references(Users.id)
    val subject = varchar("subject", SUBJECT_LENGTH)
    val message = varchar("message", MESSAGE_LENGTH)
    val status = varchar("status", STATUS_LENGTH)
    val priority = varchar("priority", PRIORITY_LENGTH)
    val ticketSource = varchar("source", SOURCE_LENGTH).default("USER")
    val createdAt = long("createdAt")
    val updatedAt = long("updatedAt")

    override val primaryKey = PrimaryKey(id)
}

data class Ticket(
    val ticketID: Int,
    val userID: Int,
    val subject: String,
    val message: String,
    val status: String,
    val priority: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

object TicketRepository {
    private fun ResultRow.toTicket() =
        Ticket(
            ticketID = this[Tickets.id],
            userID = this[Tickets.userID],
            subject = this[Tickets.subject],
            message = this[Tickets.message],
            status = this[Tickets.status],
            priority = this[Tickets.priority],
            source = this[Tickets.ticketSource],
            createdAt = this[Tickets.createdAt],
            updatedAt = this[Tickets.updatedAt],
        )

    fun all(): List<Ticket> =
        transaction {
            Tickets.selectAll().map { it.toTicket() }
        }

    fun allFull(): List<TicketFull> =
        transaction {
            Tickets
                .innerJoin(Users, { Tickets.userID }, { Users.id })
                .selectAll()
                .map {
                    TicketFull(
                        ticket = it.toTicket(),
                        user = UserRepository.run { it.toUser() },
                    )
                }
        }

    fun allFullBySource(source: String): List<TicketFull> =
        transaction {
            Tickets
                .innerJoin(Users, { Tickets.userID }, { Users.id })
                .selectAll()
                .where { Tickets.ticketSource eq source }
                .map {
                    TicketFull(
                        ticket = it.toTicket(),
                        user = UserRepository.run { it.toUser() },
                    )
                }
        }

    fun getFull(id: Int): TicketFull? =
        transaction {
            Tickets
                .innerJoin(Users, { Tickets.userID }, { Users.id })
                .selectAll()
                .where { Tickets.id eq id }
                .map {
                    TicketFull(
                        ticket = it.toTicket(),
                        user = UserRepository.run { it.toUser() },
                    )
                }.singleOrNull()
        }

    fun get(id: Int): Ticket? =
        transaction {
            Tickets
                .selectAll()
                .where { Tickets.id eq id }
                .map { it.toTicket() }
                .singleOrNull()
        }

    fun create(
        userID: Int,
        subject: String,
        message: String,
        status: String = "OPEN",
        priority: String = "NORMAL",
        source: String = "USER",
        createdAt: Long = System.currentTimeMillis(),
    ): Ticket =
        transaction {
            val id =
                Tickets.insert {
                    it[Tickets.userID] = userID
                    it[Tickets.subject] = subject
                    it[Tickets.message] = message
                    it[Tickets.status] = status
                    it[Tickets.priority] = priority
                    it[Tickets.ticketSource] = source
                    it[Tickets.createdAt] = createdAt
                    it[Tickets.updatedAt] = createdAt
                } get Tickets.id

            Ticket(id, userID, subject, message, status, priority, source, createdAt, createdAt)
        }

    fun updateStatus(
        id: Int,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Boolean =
        transaction {
            Tickets.update({ Tickets.id eq id }) {
                it[Tickets.status] = status
                it[Tickets.updatedAt] = updatedAt
            } > 0
        }

    fun delete(id: Int): Boolean =
        transaction {
            Tickets.deleteWhere { Tickets.id eq id } > 0
        }
}

data class TicketFull(
    val ticket: Ticket,
    val user: User,
) {
    val createdAtText: String = formatTicketTime(ticket.createdAt)
    val updatedAtText: String = formatTicketTime(ticket.updatedAt)
}

private val ticketTimeFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("dd MMM yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

private fun formatTicketTime(value: Long): String = ticketTimeFormatter.format(Instant.ofEpochMilli(value))
