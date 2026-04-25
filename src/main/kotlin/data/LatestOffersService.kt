package data

import java.io.File
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.random.Random

/**
 * Homepage “latest offers” strip: Hong Kong first, then other destinations in a fixed random order.
 * If the user’s origin is the same city as a card destination, we swap in an alternate row from the DB.
 * Card prices reuse [FlightScheduleRepository] (Economy Light), same idea as the search results page.
 *
 * Data lives in `data/db/offer_destinations.db` (no CSV here).
 */
object LatestOffersService {
    data class OfferCard(
        val destinationKey: String,
        val destinationName: String,
        val bookAirport: String,
        val priceGbp: Int,
        val imageUrls: List<String>,
    ) {
        val imageUrl: String? get() = imageUrls.firstOrNull()

        /** Delimiter for HTML `data-images` (split in JS for the custom lightbox). */
        val imageUrlsJoined: String get() = imageUrls.joinToString("|||GLIDE|||")
    }

    private val destinationsDb = File("data/db/offer_destinations.db")
    private val destinationOrderSeed = 0x4F46465253485546L

    private data class DestinationRow(val key: String, val displayName: String, val imageUrls: List<String>)

    private data class DestinationRecord(
        val key: String,
        val displayName: String,
        val imageUrls: List<String>,
        val category: String,
    )

    private fun loadDbRows(): List<DestinationRecord> {
        if (!destinationsDb.exists()) return emptyList()
        val jdbcUrl = "jdbc:sqlite:${destinationsDb.path}"
        return runCatching {
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.prepareStatement(
                    "SELECT destination_key, display_name, category, gallery_urls FROM offer_destinations",
                ).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val key = rs.getString("destination_key")?.trim().orEmpty()
                                val displayName = rs.getString("display_name")?.trim().orEmpty()
                                val category = rs.getString("category")?.trim()?.lowercase().orEmpty()
                                val gallery = rs.getString("gallery_urls")?.trim().orEmpty()
                                val imageUrls =
                                    gallery.split('|').map { it.trim() }.filter {
                                        it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://"))
                                    }
                                if (key.isNotBlank() && displayName.isNotBlank() && category.isNotBlank() && imageUrls.isNotEmpty()) {
                                    add(DestinationRecord(key = key, displayName = displayName, imageUrls = imageUrls, category = category))
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun loadRows(): List<DestinationRecord> = loadDbRows()

    private fun loadDestinations(): List<DestinationRow> =
        loadRows()
            .filter { it.category == "primary" }
            .map { DestinationRow(it.key, it.displayName, it.imageUrls) }

    private fun loadAlternates(): List<DestinationRow> =
        loadRows()
            .filter { it.category == "alternate" }
            .map { DestinationRow(it.key, it.displayName, it.imageUrls) }

    private fun orderedDestinations(all: List<DestinationRow>): List<DestinationRow> {
        val hk = all.find { it.key == "hong_kong" }
        val others = all.filter { it.key != "hong_kong" }
        val shuffledOthers = others.shuffled(Random(destinationOrderSeed))
        return listOfNotNull(hk) + shuffledOthers
    }

    private fun excludedDestinationKeysForOrigin(originCode: String): Set<String> {
        return when (originCode.uppercase()) {
            "HKG" -> setOf("hong_kong")
            "BKK" -> setOf("bangkok")
            "SIN" -> setOf("singapore")
            "NRT", "HND" -> setOf("tokyo")
            "DXB" -> setOf("dubai")
            "SYD" -> setOf("sydney")
            "LAX" -> setOf("los_angeles")
            "JFK", "EWR", "LGA" -> setOf("new_york")
            "CDG", "ORY" -> setOf("paris")
            "BCN" -> setOf("barcelona")
            "YVR" -> setOf("vancouver")
            "FCO", "CIA" -> setOf("rome")
            "ICN", "GMP" -> setOf("seoul")
            "IST", "SAW" -> setOf("istanbul")
            else -> emptySet()
        }
    }

    private fun bookAirportForDestinationKey(key: String): String =
        when (key) {
            "hong_kong" -> "Hong Kong (HKG)"
            "bangkok" -> "Bangkok (BKK)"
            "singapore" -> "Singapore (SIN)"
            "tokyo" -> "Tokyo (NRT)"
            "dubai" -> "Dubai (DXB)"
            "sydney" -> "Sydney (SYD)"
            "los_angeles" -> "Los Angeles (LAX)"
            "new_york" -> "New York (JFK)"
            "paris" -> "Paris (CDG)"
            "barcelona" -> "Barcelona (BCN)"
            "vancouver" -> "Vancouver (YVR)"
            "rome" -> "Rome (FCO)"
            "seoul" -> "Seoul (ICN)"
            "istanbul" -> "Istanbul (IST)"
            else -> "Hong Kong (HKG)"
        }

    private fun destinationAirportCodeForKey(key: String): String? =
        when (key) {
            "hong_kong" -> "HKG"
            "bangkok" -> "BKK"
            "singapore" -> "SIN"
            "tokyo" -> "NRT"
            "dubai" -> "DXB"
            "sydney" -> "SYD"
            "los_angeles" -> "LAX"
            "new_york" -> "JFK"
            "paris" -> "CDG"
            "barcelona" -> "BCN"
            "vancouver" -> "YVR"
            "rome" -> "FCO"
            "seoul" -> "ICN"
            "istanbul" -> "IST"
            else -> null
        }

    fun cardsForOrigin(
        originCode: String,
        departDate: LocalDate = LocalDate.now().plusDays(14),
    ): List<OfferCard> {
        val primary = loadDestinations()
        if (primary.isEmpty()) return emptyList()
        val alternates = loadAlternates().toMutableList()
        val excluded = excludedDestinationKeysForOrigin(originCode)
        val ordered = orderedDestinations(primary)
        return ordered.mapNotNull { row ->
            val effective =
                if (row.key in excluded) {
                    if (alternates.isNotEmpty()) alternates.removeAt(0) else null
                } else {
                    row
                } ?: return@mapNotNull null
            val destCode = destinationAirportCodeForKey(effective.key) ?: return@mapNotNull null
            val price =
                FlightScheduleRepository
                    .lowestEconomyLightFare(originCode = originCode, destCode = destCode, depart = departDate)
                    ?.toInt()
                    ?: return@mapNotNull null
            OfferCard(
                destinationKey = effective.key,
                destinationName = effective.displayName,
                bookAirport = bookAirportForDestinationKey(effective.key),
                priceGbp = price,
                imageUrls = effective.imageUrls,
            )
        }
    }
}
