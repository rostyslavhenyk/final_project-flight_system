package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction

private const val STATUS_LENGTH = 20

object Bookings : Table("bookings") {
    val id = integer("id").autoIncrement()

    val flightID = integer("flightID").references(Flights.id)
    val userID = integer("userID").references(Users.id)
    val seatID = integer("seatID").references(Seats.id)

    val purchaseID = integer("purchaseID").references(Purchases.id).nullable()

    val status = varchar("status", STATUS_LENGTH)

    val createdAt = long("createdAt")

    override val primaryKey = PrimaryKey(id)
}

data class Booking(
    val bookingID: Int,
    val flightID: Int,
    val userID: Int,
    val seatID: Int,
    val purchaseID: Int?,
    val status: String,
    val createdAt: Long,
)

object BookingRepository {
    internal fun ResultRow.toBooking() =
        Booking(
            bookingID = this[Bookings.id],
            flightID = this[Bookings.flightID],
            userID = this[Bookings.userID],
            seatID = this[Bookings.seatID],
            purchaseID = this[Bookings.purchaseID],
            status = this[Bookings.status],
            createdAt = this[Bookings.createdAt],
        )

    fun create(
        flightID: Int,
        userID: Int,
        seatID: Int,
        status: String = "PENDING",
        createdAt: Long = System.currentTimeMillis(),
    ): Booking =
        transaction {
            val id =
                Bookings.insert {
                    it[Bookings.flightID] = flightID
                    it[Bookings.userID] = userID
                    it[Bookings.seatID] = seatID
                    it[Bookings.status] = status
                    it[Bookings.createdAt] = createdAt
                } get Bookings.id

            Booking(id, flightID, userID, seatID, null, status, createdAt)
        }

    fun allFull(): List<BookingFull> =
        transaction {
            val purchasesById =
                Purchases
                    .selectAll()
                    .map { PurchaseRepository.run { it.toPurchase() } }
                    .associateBy { it.purchaseID }

            (
                Bookings
                    .innerJoin(Flights, { Bookings.flightID }, { Flights.id })
                    .innerJoin(Users, { Bookings.userID }, { Users.id })
                    .innerJoin(Seats, { Bookings.seatID }, { Seats.id })
            ).selectAll()
                .map {
                    val booking = it.toBooking()

                    BookingFull(
                        booking = booking,
                        flight = FlightRepository.run { it.toFlight() },
                        user = UserRepository.run { it.toUser() },
                        seat = SeatRepository.run { it.toSeat() },
                        purchase = booking.purchaseID?.let { purchaseID -> purchasesById[purchaseID] },
                    )
                }
        }

    fun allFullByUser(userID: Int): List<BookingFull> =
        transaction {
            val purchasesById =
                Purchases
                    .selectAll()
                    .map { PurchaseRepository.run { it.toPurchase() } }
                    .associateBy { it.purchaseID }

            (
                Bookings
                    .innerJoin(Flights, { Bookings.flightID }, { Flights.id })
                    .innerJoin(Users, { Bookings.userID }, { Users.id })
                    .innerJoin(Seats, { Bookings.seatID }, { Seats.id })
            ).selectAll()
                .where { Bookings.userID eq userID }
                .map {
                    val booking = it.toBooking()

                    BookingFull(
                        booking = booking,
                        flight = FlightRepository.run { it.toFlight() },
                        user = UserRepository.run { it.toUser() },
                        seat = SeatRepository.run { it.toSeat() },
                        purchase = booking.purchaseID?.let { purchaseID -> purchasesById[purchaseID] },
                    )
                }
        }

    fun attachToPurchase(
        bookingID: Int,
        purchaseID: Int,
    ): Boolean =
        transaction {
            Bookings.update({ Bookings.id eq bookingID }) {
                it[Bookings.purchaseID] = purchaseID
                it[Bookings.status] = "PAID"
            } > 0
        }

    fun get(id: Int): Booking? =
        transaction {
            Bookings
                .selectAll()
                .where { Bookings.id eq id }
                .map { it.toBooking() }
                .singleOrNull()
        }

    fun delete(id: Int): Boolean =
        transaction {
            Bookings.deleteWhere { Bookings.id eq id } > 0
        }
}

data class BookingFull(
    val booking: Booking,
    val flight: Flight,
    val user: User,
    val seat: Seat,
    val purchase: Purchase?,
)
