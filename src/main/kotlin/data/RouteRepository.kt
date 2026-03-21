package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger


data class Route(
    val routeID: Int,
    val departureAirportID: Int, //FK
    val arrivalAirportID: Int, //FK
)

object RouteRepository {
    private val file = File("data/routes.csv")
    private val routes = mutableListOf<Route>()
    private val idCounter = AtomicInteger(1)
    private val csvHeader = "routeID,departureAirportID,arrivalAirportID\n"

    val size: Int
        get() = routes.size

    val nullRoute: Route
        get() = Route(-1, -1, -1)

    init {
        file.parentFile?.mkdirs()

        if (!file.exists()) {
            // If CSV doesn’t exist, create it with header only 
            file.writeText(csvHeader)
        } else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 3)
                if (parts.size == 3) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    val dep = parts[1].toIntOrNull() ?: return@forEach
                    val arr = parts[2].toIntOrNull() ?: return@forEach

                    routes.add(Route(id, dep, arr))
                    idCounter.set(maxOf(idCounter.get(), id + 1))
                }
            }
        }
    }

    fun all(): List<Route> = routes.toList()

    fun get(id: Int): Route = routes.find { it.routeID == id } ?: nullRoute

    fun add(departureAirportID: Int, arrivalAirportID: Int): Route {
        require(AirportRepository.get(departureAirportID).airportID != -1) {
            "Departure airport does not exist"
        }
        require(AirportRepository.get(arrivalAirportID).airportID != -1) {
            "Arrival airport does not exist"
        }

        val route = Route(
            routeID = idCounter.getAndIncrement(),
            departureAirportID = departureAirportID,
            arrivalAirportID = arrivalAirportID
        )

        routes.add(route)
        persist()
        return route
    }

    fun delete(id: Int): Boolean {
        val removed = routes.removeIf { it.routeID == id }
        if (removed) persist()
        return removed
    }

    // Persist routes to CSV
    private fun persist() {
        file.writeText(
            csvHeader +
                    routes.joinToString("\n") { "${it.routeID},${it.departureAirportID},${it.arrivalAirportID}" }
        )
    }
}