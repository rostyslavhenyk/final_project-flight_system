package data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object BoardingPasses : Table("boarding_passes") {
    val id = integer("id").autoIncrement()

    val bookingId = integer("bookingID").references(Bookings.id)

    val gate = varchar("gate", 10)

    val boardingTime = long("boardingTime")

    val issuedAt = long("issuedAt")

    override val primaryKey = PrimaryKey(id)
}

data class BoardingPass(
    val id: Int,
    val bookingId: Int,
    val gate: String,
    val boardingTime: Long,
    val issuedAt: Long,
)

object BoardingPassRepository {
    val nullBoardingPass = BoardingPass(-1, -1, "", 0L, 0L)

    private fun ResultRow.toBoardingPass() =
        BoardingPass(
            id = this[BoardingPasses.id],
            bookingId = this[BoardingPasses.bookingId],
            gate = this[BoardingPasses.gate],
            boardingTime = this[BoardingPasses.boardingTime],
            issuedAt = this[BoardingPasses.issuedAt],
        )

    fun create(
        bookingId: Int,
        gate: String,
        boardingTime: Long,
        issuedAt: Long = System.currentTimeMillis(),
    ): BoardingPass =
        transaction {
            val id =
                BoardingPasses.insert {
                    it[BoardingPasses.bookingId] = bookingId
                    it[BoardingPasses.gate] = gate
                    it[BoardingPasses.boardingTime] = boardingTime
                    it[BoardingPasses.issuedAt] = issuedAt
                } get BoardingPasses.id

            BoardingPass(id, bookingId, gate, boardingTime, issuedAt)
        }

    fun getByBooking(bookingId: Int): BoardingPass? =
        transaction {
            BoardingPasses
                .selectAll()
                .where { BoardingPasses.bookingId eq bookingId }
                .map { it.toBoardingPass() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            BoardingPasses.deleteWhere { BoardingPasses.id eq id } > 0
        }
}
