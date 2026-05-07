package data

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

private const val STATUS_RESERVED = "RESERVED"
private const val STATUS_CONFIRMED = "CONFIRMED"

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

    internal fun ResultRow.toSeat() =
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

    fun createConfirmed(
        userIdValue: Int,
        flightIdValue: Int,
        row: Int,
        seat: String,
        createdAt: Long = Instant.now().toEpochMilli(),
    ): Seat? =
        transaction {
            releaseExpiredInsideTransaction(createdAt)
            val existing = seatRow(flightIdValue, row, seat)
            when {
                existing == null -> insertSeat(userIdValue, flightIdValue, row, seat, STATUS_CONFIRMED, createdAt)
                existing[Seats.status] == STATUS_CONFIRMED -> null
                existing[Seats.userId] == userIdValue -> {
                    Seats.update({ Seats.id eq existing[Seats.id] }) {
                        it[userId] = userIdValue
                        it[status] = STATUS_CONFIRMED
                        it[expiresAt] = createdAt
                    }
                    Seat(existing[Seats.id], flightIdValue, userIdValue, row, seat, STATUS_CONFIRMED, createdAt)
                }
                existing[Seats.expiresAt] < createdAt ->
                    replaceExpiredSeat(userIdValue, existing, flightIdValue, row, seat, createdAt)
                else -> null
            }
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
            releaseExpiredInsideTransaction(now)
            val existing = seatRow(flightIdValue, row, seat)
            when {
                existing == null -> {
                    insertSeat(userIdValue, flightIdValue, row, seat, STATUS_RESERVED, now + holdMillis)
                    true
                }
                existing[Seats.userId] == userIdValue && existing[Seats.status] == STATUS_RESERVED -> {
                    Seats.update({ Seats.id eq existing[Seats.id] }) {
                        it[expiresAt] = now + holdMillis
                    }
                    true
                }
                else -> false
            }
        }

    fun release(
        userIdValue: Int,
        flightIdValue: Int,
        row: Int,
        seat: String,
    ): Boolean =
        transaction {
            Seats.deleteWhere {
                (Seats.flightId eq flightIdValue) and
                    (Seats.rowNumber eq row) and
                    (Seats.seatLetter eq seat) and
                    (Seats.userId eq userIdValue) and
                    (Seats.status eq STATUS_RESERVED)
            } > 0
        }

    fun releaseExpired(): Int =
        transaction {
            releaseExpiredInsideTransaction(Instant.now().toEpochMilli())
        }

    fun unavailableForFlights(
        flightIds: Collection<Int>,
        userIdValue: Int?,
    ): List<Seat> =
        transaction {
            releaseExpiredInsideTransaction(Instant.now().toEpochMilli())
            flightIds
                .flatMap { flightIdValue -> allUnavailableForFlight(flightIdValue, userIdValue) }
        }

    fun isUnavailableForUser(
        flightIdValue: Int,
        row: Int,
        seat: String,
        userIdValue: Int?,
    ): Boolean =
        transaction {
            releaseExpiredInsideTransaction(Instant.now().toEpochMilli())
            seatRow(flightIdValue, row, seat)?.let { existing ->
                existing[Seats.status] == STATUS_CONFIRMED ||
                    (existing[Seats.status] == STATUS_RESERVED && existing[Seats.userId] != userIdValue)
            } == true
        }

    fun delete(idValue: Int): Boolean =
        transaction {
            Seats.deleteWhere { Seats.id eq idValue } > 0
        }
}

object SeatMaintenance {
    fun ensureUniqueSeatIndex() {
        transaction {
            runCatching {
                TransactionManager.current().exec(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_seats_flight_row_letter_unique " +
                        "ON seats(flightID, rowNumber, seatLetter)",
                )
            }
        }
    }
}

private fun seatRow(
    flightIdValue: Int,
    row: Int,
    seat: String,
): ResultRow? =
    Seats
        .selectAll()
        .where {
            (Seats.flightId eq flightIdValue) and
                (Seats.rowNumber eq row) and
                (Seats.seatLetter eq seat)
        }.singleOrNull()

private fun insertSeat(
    userIdValue: Int,
    flightIdValue: Int,
    row: Int,
    seat: String,
    statusValue: String,
    expiresAtValue: Long,
): Seat {
    val id =
        Seats.insert {
            it[userId] = userIdValue
            it[flightId] = flightIdValue
            it[rowNumber] = row
            it[seatLetter] = seat
            it[status] = statusValue
            it[expiresAt] = expiresAtValue
        } get Seats.id
    return Seat(id, flightIdValue, userIdValue, row, seat, statusValue, expiresAtValue)
}

private fun replaceExpiredSeat(
    userIdValue: Int,
    existing: ResultRow,
    flightIdValue: Int,
    row: Int,
    seat: String,
    createdAt: Long,
): Seat {
    Seats.update({ Seats.id eq existing[Seats.id] }) {
        it[userId] = userIdValue
        it[status] = STATUS_CONFIRMED
        it[expiresAt] = createdAt
    }
    return Seat(existing[Seats.id], flightIdValue, userIdValue, row, seat, STATUS_CONFIRMED, createdAt)
}

private fun releaseExpiredInsideTransaction(now: Long): Int =
    Seats.deleteWhere {
        (Seats.status eq STATUS_RESERVED) and
            (Seats.expiresAt less now)
    }

private fun allUnavailableForFlight(
    flightIdValue: Int,
    userIdValue: Int?,
): List<Seat> =
    Seats
        .selectAll()
        .where {
            (Seats.flightId eq flightIdValue) and
                (
                    (Seats.status eq STATUS_CONFIRMED) or
                        ((Seats.status eq STATUS_RESERVED) and (Seats.userId neq userIdValue))
                )
        }.map { SeatRepository.run { it.toSeat() } }
