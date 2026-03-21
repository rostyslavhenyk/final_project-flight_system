package data

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class Airport(
    val airportID: Int,
    var countryID: Int,
    var city: String,
    var name: String,
    var code: String
)

object AirportRepository {
    private val file = File("data/airport.csv")
    private val airports = mutableListOf<Airport>()
    private val idCounter = AtomicInteger(1)
    private val csvHeader = "airportID,countryID,city,name,code\n"

    val size: Int
        get() = airports.size

    //if searching an airport fails display following
    val nullAirport: Airport
        get() = Airport(-1, -1, "", "", "")

    init {
        file.parentFile?.mkdirs()

        // If CSV doesn’t exist, create it with header only 
        if (!file.exists()) {
            file.writeText(csvHeader)
        }
        else {
            file.readLines().drop(1).forEach { line ->
                val parts = line.split(",", limit = 5)
                if (parts.size == 5) {
                    val id = parts[0].toIntOrNull() ?: return@forEach
                    val countryID = parts[1].toIntOrNull() ?: return@forEach

                    airports.add(
                        Airport(
                            airportID = id,
                            countryID = countryID,
                            city = parts[2],
                            name = parts[3],
                            code = parts[4]
                        )
                    )

                    idCounter.set(maxOf(idCounter.get(), id + 1))
                }
            }
        }
    }

    fun all(): List<Airport> = airports.toList()

    fun get(id: Int): Airport {
        return airports.find { it.airportID == id } ?: nullAirport
    }

    fun add(countryID: Int, city: String, name: String, code: String): Airport {
        require(CountryRepository.get(countryID) != null) {
            "Invalid countryID"
        }

        val airport = Airport(
            airportID = idCounter.getAndIncrement(),
            countryID = countryID,
            city = city,
            name = name,
            code = code
        )

        airports.add(airport)
        persist()
        return airport
    }

    fun delete(id: Int): Boolean {
        val removed = airports.removeIf { it.airportID == id }
        if (removed) persist()
        return removed
    }

    private fun persist() {
        file.writeText(
            csvHeader +
            airports.joinToString("\n") {
                "${it.airportID},${it.countryID}," +
                "${it.city.replace(",", "")}," +
                "${it.name.replace(",", "")}," +
                it.code
            }
        )
    }
}


// lets you use Airport.countryName to access a country's name
val Airport.countryName: String
    get() = CountryRepository.get(this.countryID)?.name ?: "Unknown"