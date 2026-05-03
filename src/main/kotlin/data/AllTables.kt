package data

import data.flight.FlightScheduleTemplates

object AllTables {
    fun all() =
        arrayOf(
            Users,
            Airports,
            Routes,
            FlightScheduleTemplates,
            Flights,
            Countries,
            Seats,
            Bookings,
            Passengers,
            BoardingPasses,
            LoyaltyUsers,
            Tickets,
            TicketImages,
            Purchases,
            Payments,
        )
}
