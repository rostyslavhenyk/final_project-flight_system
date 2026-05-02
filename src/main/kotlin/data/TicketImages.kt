package data

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object TicketImages : Table("ticket_images") {
    private const val FILENAME_LENGTH = 255
    private const val CONTENT_TYPE_LENGTH = 128
    private const val PATH_LENGTH = 512

    val id = integer("id").autoIncrement()
    val ticketID = integer("ticketID").references(Tickets.id)
    val filename = varchar("filename", FILENAME_LENGTH)
    val contentType = varchar("contentType", CONTENT_TYPE_LENGTH)
    val path = varchar("path", PATH_LENGTH)
    val size = long("size")
    val uploadedAt = long("uploadedAt")

    override val primaryKey = PrimaryKey(id)
}

data class TicketImage(
    val id: Int,
    val ticketID: Int,
    val filename: String,
    val contentType: String,
    val path: String,
    val size: Long,
    val uploadedAt: Long,
)

object TicketImageRepository {
    private fun ResultRow.toTicketImage() =
        TicketImage(
            id = this[TicketImages.id],
            ticketID = this[TicketImages.ticketID],
            filename = this[TicketImages.filename],
            contentType = this[TicketImages.contentType],
            path = this[TicketImages.path],
            size = this[TicketImages.size],
            uploadedAt = this[TicketImages.uploadedAt],
        )

    fun allForTicket(ticketID: Int): List<TicketImage> =
        transaction {
            TicketImages
                .selectAll()
                .where { TicketImages.ticketID eq ticketID }
                .map { it.toTicketImage() }
        }

    fun get(id: Int): TicketImage? =
        transaction {
            TicketImages
                .selectAll()
                .where { TicketImages.id eq id }
                .map { it.toTicketImage() }
                .singleOrNull()
        }

    fun create(
        ticketID: Int,
        filename: String,
        contentType: String,
        path: String,
        size: Long,
        uploadedAt: Long = System.currentTimeMillis(),
    ): TicketImage =
        transaction {
            val id =
                TicketImages.insert {
                    it[TicketImages.ticketID] = ticketID
                    it[TicketImages.filename] = filename
                    it[TicketImages.contentType] = contentType
                    it[TicketImages.path] = path
                    it[TicketImages.size] = size
                    it[TicketImages.uploadedAt] = uploadedAt
                } get TicketImages.id

            TicketImage(id, ticketID, filename, contentType, path, size, uploadedAt)
        }

    fun deleteForTicket(ticketID: Int): Boolean =
        transaction {
            TicketImages.deleteWhere { TicketImages.ticketID eq ticketID } > 0
        }
}
