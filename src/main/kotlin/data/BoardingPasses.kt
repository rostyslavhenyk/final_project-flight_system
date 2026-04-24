package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object BoardingPasses : Table("boarding_passes") {
    private const val GATE_NAME = 10

    val id = integer("id").autoIncrement()

    val bookingID = integer("bookingID").references(Bookings.id)

    val gate = varchar("gate", GATE_NAME)

    val boardingTime = long("boardingTime")

    val issuedAt = long("issuedAt")

    override val primaryKey = PrimaryKey(id)
}

data class BoardingPass(
    val id: Int,
    val bookingID: Int,
    val gate: String,
    val boardingTime: Long,
    val issuedAt: Long,
)

object BoardingPassRepository {
    private fun ResultRow.toBoardingPass() =
        BoardingPass(
            id = this[BoardingPasses.id],
            bookingID = this[BoardingPasses.bookingID],
            gate = this[BoardingPasses.gate],
            boardingTime = this[BoardingPasses.boardingTime],
            issuedAt = this[BoardingPasses.issuedAt],
        )

    fun create(
        bookingID: Int,
        gate: String,
        boardingTime: Long,
        issuedAt: Long = System.currentTimeMillis(),
    ): BoardingPass =
        transaction {
            val id =
                BoardingPasses.insert {
                    it[BoardingPasses.bookingID] = bookingID
                    it[BoardingPasses.gate] = gate
                    it[BoardingPasses.boardingTime] = boardingTime
                    it[BoardingPasses.issuedAt] = issuedAt
                } get BoardingPasses.id

            BoardingPass(id, bookingID, gate, boardingTime, issuedAt)
        }

    fun getByBooking(bookingID: Int): BoardingPass? =
        transaction {
            BoardingPasses
                .selectAll()
                .where { BoardingPasses.bookingID eq bookingID }
                .map { it.toBoardingPass() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            BoardingPasses.deleteWhere { BoardingPasses.id eq id } > 0
        }
}
