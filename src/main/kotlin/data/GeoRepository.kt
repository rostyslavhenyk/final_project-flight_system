package data

import java.io.File
import kotlin.math.pow

/**
 * Airport with coordinates for "nearest airport" from user location.
 * Data in data/airports_geo.csv: code,name,lat,lon
 */
data class AirportGeo(val code: String, val name: String, val lat: Double, val lon: Double)

object GeoRepository {
    private val file = File("data/airports_geo.csv")
    private val airports: List<AirportGeo> by lazy {
        file.parentFile?.mkdirs()
        if (!file.exists()) return@lazy emptyList()
        file.readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size != 4) return@mapNotNull null
                val lat = parts[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[3].trim().toDoubleOrNull() ?: return@mapNotNull null
                AirportGeo(parts[0].trim(), parts[1].trim(), lat, lon)
            }
    }

    /** Returns the nearest airport to (lat, lon) or null if no data. */
    fun nearestAirport(lat: Double, lon: Double): AirportGeo? {
        if (airports.isEmpty()) return null
        return airports.minByOrNull { a ->
            (a.lat - lat).pow(2) + (a.lon - lon).pow(2)
        }
    }

    fun allGeo(): List<AirportGeo> = airports

    /** Lat/lon for an IATA code, for distance calculations. */
    fun coordinatesForAirport(code: String): Pair<Double, Double>? {
        val c = code.trim()
        return airports.find { it.code.equals(c, ignoreCase = true) }?.let { it.lat to it.lon }
    }
}
