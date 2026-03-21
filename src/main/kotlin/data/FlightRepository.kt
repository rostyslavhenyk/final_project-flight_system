package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger


data class Flight(
    val flightID: Int,
    val routeID: Int,
    val departureTime: String,
    val arrivalTime: String,
    val price: Double,
    val status: String,
    //val seatCapacity gotta see
)

object FlightRepository {
    private val file = File("data/flights.csv")
    private val flights = mutableListOf<Flight>()
    private val idCounter = AtomicInteger(1)
    private val csvHeader = "flightID,routeID,departureTime,arrivalTime,price,status\n"

    val size: Int
        get() = flights.size

    val nullFlight: Flight
        get() = Flight(-1, -1, "", "", 0.0, "Unknown")

    init {
        file.parentFile?.mkdirs()

        if (!file.exists()) {
            file.writeText(csvHeader)
        } else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 6)
                if (parts.size == 6) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    val routeID = parts[1].toIntOrNull() ?: return@forEach
                    val departureTime = parts[2]
                    val arrivalTime = parts[3]
                    val price = parts[4].toDoubleOrNull() ?: 0.0
                    val status = parts[5]

                    flights.add(Flight(id, routeID, departureTime, arrivalTime, price, status))
                    idCounter.set(maxOf(idCounter.get(), id + 1))
                }
            }
        }
    }

    fun all(): List<Flight> = flights.toList()

    fun get(id: Int): Flight = flights.find { it.flightID == id } ?: nullFlight

    fun add(
        routeID: Int,
        departureTime: String,
        arrivalTime: String,
        price: Double,
        status: String
    ): Flight {
        // Validate that route exists
        require(RouteRepository.get(routeID).routeID != -1) { "Invalid routeID" }

        val flight = Flight(
            flightID = idCounter.getAndIncrement(),
            routeID = routeID,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            price = price,
            status = status
        )

        flights.add(flight)
        persist()
        return flight
    }

    fun delete(id: Int): Boolean {
        val removed = flights.removeIf { it.flightID == id }
        if (removed) persist()
        return removed
    }

    private fun persist() {
        file.writeText(
            csvHeader +
            flights.joinToString("\n") {
                "${it.flightID},${it.routeID},${it.departureTime},${it.arrivalTime},${it.price},${it.status}"
            }
        )
    }
}

// lets you use route related information, alongside accessing departure or arrival airport information
val Flight.route: Route?
    get() = RouteRepository.get(this.routeID)

val Flight.departureAirport: Airport?
    get() = this.route?.departureAirport

val Flight.arrivalAirport: Airport?
    get() = this.route?.arrivalAirport