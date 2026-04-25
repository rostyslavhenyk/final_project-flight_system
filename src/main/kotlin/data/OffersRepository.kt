package data

import java.io.File
import java.sql.DriverManager

/**
 * One row from `data/db/offers.db`: origin airport code, destination name, cabin class, price in GBP.
 */
data class Offer(
    val originCode: String,
    val destinationName: String,
    val cabinClass: String,
    val priceGbp: Int,
)

/**
 * Loads offers from SQLite only: `data/db/offers.db` (no CSV fallback).
 */
object OffersRepository {
    private val sqliteFile = File("data/db/offers.db")

    fun all(): List<Offer> = loadOffers(null)

    fun byOrigin(originCode: String): List<Offer> = loadOffers(originCode)

    private fun loadOffers(originFilter: String?): List<Offer> = loadFromSqlite(originFilter)

    private fun loadFromSqlite(originFilter: String?): List<Offer> {
        if (!sqliteFile.exists()) return emptyList()
        val jdbcUrl = "jdbc:sqlite:${sqliteFile.path}"
        return runCatching {
            DriverManager.getConnection(jdbcUrl).use { conn ->
                val sql =
                    if (originFilter == null) {
                        "SELECT origin_code, destination_name, cabin_class, price_gbp FROM offers"
                    } else {
                        "SELECT origin_code, destination_name, cabin_class, price_gbp FROM offers WHERE origin_code = ?"
                    }
                conn.prepareStatement(sql).use { stmt ->
                    if (originFilter != null) {
                        stmt.setString(1, originFilter.trim())
                    }
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val origin = rs.getString("origin_code")?.trim().orEmpty()
                                val dest = rs.getString("destination_name")?.trim().orEmpty()
                                val cabin = rs.getString("cabin_class")?.trim().orEmpty()
                                val priceStr = rs.getString("price_gbp")?.trim().orEmpty()
                                val price = priceStr.toIntOrNull() ?: continue
                                if (origin.isNotBlank() && dest.isNotBlank() && cabin.isNotBlank()) {
                                    add(Offer(origin, dest, cabin, price))
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }
}
