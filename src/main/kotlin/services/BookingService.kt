package services

import data.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

object BookingService {
    fun createBooking(
        flightID: Int,
        userID: Int,
        seatID: Int,
    ): Booking =
        transaction {
            val now = Instant.now().toEpochMilli()

            val seat =
                Seats
                    .selectAll()
                    .where { Seats.id eq seatID }
                    .singleOrNull()
                    ?: error("Seat not found")

            if (seat[Seats.flightId] != flightID) {
                error("Seat does not belong to flight")
            }

            val isValidSeat =
                seat[Seats.status] == "RESERVED" &&
                    seat[Seats.expiresAt] > now

            if (!isValidSeat) {
                error("Seat not available")
            }

            val alreadyBooked =
                Bookings
                    .selectAll()
                    .where { Bookings.seatID eq seatID }
                    .any()

            if (alreadyBooked) {
                error("Seat already booked")
            }

            val bookingId =
                Bookings.insert {
                    it[Bookings.flightID] = flightID
                    it[Bookings.userID] = userID
                    it[Bookings.seatID] = seatID
                    it[Bookings.createdAt] = now
                } get Bookings.id

            Seats.update({ Seats.id eq seatID }) {
                it[status] = "CONFIRMED"
            }

            Booking(
                bookingID = bookingId,
                flightID = flightID,
                userID = userID,
                seatID = seatID,
                createdAt = now,
            )
        }
}
