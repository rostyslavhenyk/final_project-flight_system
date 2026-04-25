package data

import java.io.File
import java.sql.DriverManager
import kotlin.math.pow

/**
 * Airport with coordinates for "nearest airport" from user location.
 * Source: `data/db/airports_geo.db` table `airports_geo` only (no CSV fallback).
 */
data class AirportGeo(val code: String, val name: String, val lat: Double, val lon: Double)

object GeoRepository {
    private val sqliteFile = File("data/db/airports_geo.db")

    private val airports: List<AirportGeo> by lazy { loadFromSqlite() }

    private fun loadFromSqlite(): List<AirportGeo> {
        if (!sqliteFile.exists()) return emptyList()
        val jdbcUrl = "jdbc:sqlite:${sqliteFile.path}"
        return runCatching {
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.prepareStatement("SELECT code, name, lat, lon FROM airports_geo").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val code = rs.getString("code")?.trim().orEmpty()
                                val name = rs.getString("name")?.trim().orEmpty()
                                val lat = rs.getString("lat")?.trim()?.toDoubleOrNull()
                                val lon = rs.getString("lon")?.trim()?.toDoubleOrNull()
                                if (code.isNotBlank() && name.isNotBlank() && lat != null && lon != null) {
                                    add(AirportGeo(code, name, lat, lon))
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
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
        val airportCode = code.trim()
        return airports.find { it.code.equals(airportCode, ignoreCase = true) }?.let { it.lat to it.lon }
    }
}
