package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Passengers : Table("passengers") {
    private const val FIRST_NAME_LENGTH = 128
    private const val LAST_NAME_LENGTH = 128
    private const val PASSPORT_NUMBER_LENGTH = 24

    val id = integer("id").autoIncrement()

    val bookingId = integer("bookingID").references(Bookings.id)

    val firstName = varchar("firstName", FIRST_NAME_LENGTH)
    val lastName = varchar("lastName", LAST_NAME_LENGTH)
    val passportNumber = varchar("passportNumber", PASSPORT_NUMBER_LENGTH)

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
