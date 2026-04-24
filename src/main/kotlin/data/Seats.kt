package data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object Seats : Table("seats") {
    private const val SEAT_LETTER_LENGTH = 1
    private const val STATUS_LENGTH = 20

    val id = integer("id").autoIncrement()

    val flightId = integer("flightID").references(Flights.id)

    val userId = integer("userID").references(Users.id).nullable()

    val rowNumber = integer("rowNumber")
    val seatLetter = varchar("seatLetter", SEAT_LETTER_LENGTH)

    val status = varchar("status", STATUS_LENGTH)
    val expiresAt = long("expiresAt")

    override val primaryKey = PrimaryKey(id)
}

data class Seat(
    val id: Int,
    val flightId: Int,
    val userId: Int?, // purely for holding logic
    val rowNumber: Int,
    val seatLetter: String,
    val status: String,
    val expiresAt: Long,
)

object SeatRepository {
    private const val DEFAULT_HOLD_TIME = 600_000L // 10 minutes

    val nullSeat = Seat(-1, -1, null, -1, "", "", 0L)

    private fun ResultRow.toSeat() =
        Seat(
            id = this[Seats.id],
            flightId = this[Seats.flightId],
            userId = this[Seats.userId],
            rowNumber = this[Seats.rowNumber],
            seatLetter = this[Seats.seatLetter],
            status = this[Seats.status],
            expiresAt = this[Seats.expiresAt],
        )

    fun all(flightIdValue: Int): List<Seat> =
        transaction {
            Seats
                .selectAll()
                .where { Seats.flightId eq flightIdValue }
                .map { it.toSeat() }
        }

    fun get(
        flightIdValue: Int,
        row: Int,
        seat: String,
    ): Seat =
        transaction {
            Seats
                .selectAll()
                .where {
                    (Seats.flightId eq flightIdValue) and
                        (Seats.rowNumber eq row) and
                        (Seats.seatLetter eq seat)
                }.map { it.toSeat() }
                .singleOrNull()
                ?: nullSeat
        }

    fun hold(
        userIdValue: Int,
        flightIdValue: Int,
        row: Int,
        seat: String,
        holdMillis: Long = DEFAULT_HOLD_TIME,
    ): Boolean =
        transaction {
            val now = Instant.now().toEpochMilli()

            val alreadyTaken =
                Seats
                    .selectAll()
                    .where {
                        (Seats.flightId eq flightIdValue) and
                            (Seats.rowNumber eq row) and
                            (Seats.seatLetter eq seat) and
                            (
                                (Seats.status eq "CONFIRMED") or
                                    (
                                        (Seats.status eq "RESERVED") and
                                            (Seats.expiresAt greater now)
                                    )
                            )
                    }.any()

            if (alreadyTaken) return@transaction false

            Seats.insert {
                it[userId] = userIdValue
                it[flightId] = flightIdValue
                it[rowNumber] = row
                it[seatLetter] = seat
                it[status] = "RESERVED"
                it[expiresAt] = now + holdMillis
            }

            true
        }

    fun confirm(
        flightIdValue: Int,
        row: Int,
        seat: String,
    ): Boolean =
        transaction {
            Seats.update({
                (Seats.flightId eq flightIdValue) and
                    (Seats.rowNumber eq row) and
                    (Seats.seatLetter eq seat)
            }) {
                it[status] = "CONFIRMED"
            } > 0
        }

    fun releaseExpired(): Int =
        transaction {
            val now = Instant.now().toEpochMilli()

            Seats.deleteWhere {
                (Seats.status eq "RESERVED") and
                    (Seats.expiresAt less now)
            }
        }

    fun delete(idValue: Int): Boolean =
        transaction {
            Seats.deleteWhere { Seats.id eq idValue } > 0
        }
}
