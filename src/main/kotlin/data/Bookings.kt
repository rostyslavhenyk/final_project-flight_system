package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Bookings : Table("bookings") {
    val id = integer("id").autoIncrement()

    val flightID = integer("flightID").references(Flights.id)
    val userID = integer("userID").references(Users.id)
    val seatID = integer("seatID").references(Seats.id)

    val createdAt = long("createdAt")

    override val primaryKey = PrimaryKey(id)
}

data class Booking(
    val bookingID: Int,
    val flightID: Int,
    val userID: Int,
    val seatID: Int,
    val createdAt: Long,
)

object BookingRepository {
    val nullBooking = Booking(-1, -1, -1, -1, 0L)

    private fun ResultRow.toBooking() =
        Booking(
            bookingID = this[Bookings.id],
            flightID = this[Bookings.flightID],
            userID = this[Bookings.userID],
            seatID = this[Bookings.seatID],
            createdAt = this[Bookings.createdAt],
        )

    fun all(): List<Booking> =
        transaction {
            Bookings.selectAll().map { it.toBooking() }
        }

    fun get(id: Int): Booking =
        transaction {
            Bookings
                .selectAll()
                .where { Bookings.id eq id }
                .map { it.toBooking() }
                .singleOrNull()
                ?: nullBooking
        }

    fun add(
        flightID: Int,
        userID: Int,
        seatID: Int,
        createdAt: Long = System.currentTimeMillis(),
    ): Booking =
        transaction {
            val id =
                Bookings.insert {
                    it[Bookings.flightID] = flightID
                    it[Bookings.userID] = userID
                    it[Bookings.seatID] = seatID
                    it[Bookings.createdAt] = createdAt
                } get Bookings.id

            Booking(id, flightID, userID, seatID, createdAt)
        }

    fun delete(id: Int): Boolean =
        transaction {
            Bookings.deleteWhere { Bookings.id eq id } > 0
        }
}
