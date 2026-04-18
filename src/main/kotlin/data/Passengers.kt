package data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Passengers : Table("passengers") {
    val id = integer("id").autoIncrement()

    val bookingId = integer("bookingID").references(Bookings.id)

    val firstName = varchar("firstName", 100)

    val lastName = varchar("lastName", 100)

    val passportNumber = varchar("passportNumber", 20)

    override val primaryKey = PrimaryKey(id)
}

data class Passenger(
    val id: Int,
    val bookingId: Int,
    val firstName: String,
    val lastName: String,
    val passportNumber: String,
)

object PassengerRepository {
    val nullPassenger = Passenger(-1, -1, "", "", "")

    private fun ResultRow.toPassenger() =
        Passenger(
            id = this[Passengers.id],
            bookingId = this[Passengers.bookingId],
            firstName = this[Passengers.firstName],
            lastName = this[Passengers.lastName],
            passportNumber = this[Passengers.passportNumber],
        )

    fun add(
        bookingId: Int,
        firstName: String,
        lastName: String,
        passportNumber: String,
    ): Passenger =
        transaction {
            val id =
                Passengers.insert {
                    it[Passengers.bookingId] = bookingId
                    it[Passengers.firstName] = firstName
                    it[Passengers.lastName] = lastName
                    it[Passengers.passportNumber] = passportNumber
                } get Passengers.id

            Passenger(id, bookingId, firstName, lastName, passportNumber)
        }

    fun getByBooking(bookingId: Int): Passenger? =
        transaction {
            Passengers
                .selectAll()
                .where { Passengers.bookingId eq bookingId }
                .map { it.toPassenger() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            Passengers.deleteWhere { Passengers.id eq id } > 0
        }
}
