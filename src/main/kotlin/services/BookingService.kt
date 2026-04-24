package services

import data.Bookings
import data.Booking
import data.Seats

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
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
                error("Seat does not belong to this flight")
            }

            // Seat must be free OR expired reservation
            val isUnavailable =
                seat[Seats.status] == "CONFIRMED" ||
                    (seat[Seats.status] == "RESERVED" && seat[Seats.expiresAt] > now)

            if (isUnavailable) {
                error("Seat is not available")
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
                    it[Bookings.status] = "CONFIRMED"
                } get Bookings.id

            Seats.update({ Seats.id eq seatID }) {
                it[status] = "CONFIRMED"
                it[expiresAt] = now
            }

            Booking(
                bookingID = bookingId,
                flightID = flightID,
                userID = userID,
                seatID = seatID,
                purchaseID = null,
                status = "CONFIRMED",
                createdAt = now,
            )
        }
}
