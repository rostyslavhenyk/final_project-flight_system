package data

import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Latest-offer cards: Hong Kong first, then a stable shuffle of the rest. When the departure airport
 * matches a destination city, that slot is filled from **alternate** rows in [offer_destinations.csv].
 * Distances use great-circle km between [airports_geo.csv] and [destination_geo.csv]. Prices are
 * deterministic per (origin, destination).
 *
 * **offer_destinations.csv** columns: `destination_key`, `display_name`, `category` (`primary` or
 * `alternate`), `gallery_urls` — three URLs separated by `|` (pipe) so commas inside URLs are safe.
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

    private val destinationsFile = File("data/offer_destinations.csv")
    private val destinationGeoFile = File("data/destination_geo.csv")

    private val destinationOrderSeed = 0x4F46465253485546L
    private val priceSalt = "glide-offers-price-v1"

    private data class DestinationRow(val key: String, val displayName: String, val imageUrls: List<String>)

    private data class CsvDestination(
        val key: String,
        val displayName: String,
        val imageUrls: List<String>,
        val category: String,
    )

    private val destinationCoords: Map<String, Pair<Double, Double>> by lazy {
        if (!destinationGeoFile.exists()) return@lazy emptyMap()
        destinationGeoFile.readLines().drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(",", limit = 3)
            if (p.size != 3) return@mapNotNull null
            val lat = p[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lon = p[2].trim().toDoubleOrNull() ?: return@mapNotNull null
            p[0].trim() to (lat to lon)
        }.toMap()
    }

    /**
     * One CSV line → key, display name, category, gallery_urls (pipe-separated).
     * Uses [split limit 4] so commas inside URLs in the last column are not split incorrectly
     * (gallery must use `|` only, not commas, in this coursework format).
     */
    private fun parseDestinationCsvLine(line: String): CsvDestination? {
        val trimmed = line.trimEnd('\r').trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(",", limit = 4)
        if (parts.size < 4) return null
        val key = parts[0].trim()
        val name = parts[1].trim()
        val category = parts[2].trim().lowercase()
        val gallery = parts[3].trim()
        val urls =
            gallery.split('|').map { it.trim() }.filter {
                it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://"))
            }
        if (urls.isEmpty()) return null
        return CsvDestination(key, name, urls, category)
    }

    private fun loadCsvRows(): List<CsvDestination> {
        if (!destinationsFile.exists()) return emptyList()
        return destinationsFile.readLines().drop(1).mapNotNull { parseDestinationCsvLine(it) }
    }

    private fun loadDestinations(): List<DestinationRow> =
        loadCsvRows()
            .filter { it.category == "primary" }
            .map { DestinationRow(it.key, it.displayName, it.imageUrls) }

    private fun loadAlternates(): List<DestinationRow> =
        loadCsvRows()
            .filter { it.category == "alternate" }
            .map { DestinationRow(it.key, it.displayName, it.imageUrls) }

    private fun haversineKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Int {
        val r = 6371.0
        val φ1 = lat1 * PI / 180.0
        val φ2 = lat2 * PI / 180.0
        val Δφ = (lat2 - lat1) * PI / 180.0
        val Δλ = (lon2 - lon1) * PI / 180.0
        val a = sin(Δφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(Δλ / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).roundToInt()
    }

    private fun distanceKm(originCode: String, destKey: String): Int? {
        val o = GeoRepository.coordinatesForAirport(originCode) ?: return null
        val d = destinationCoords[destKey] ?: return null
        return haversineKm(o.first, o.second, d.first, d.second)
    }

    private fun priceBoundsGbp(distanceKm: Int): Pair<Int, Int> {
        val min = ((distanceKm * 0.055) + 320).toInt().coerceIn(280, 2500)
        val max = ((distanceKm * 0.085) + 520).toInt().coerceIn(min + 80, 3200)
        return min to max
    }

    private fun stableHash64(s: String): Long {
        var h = 1469598103934665603L
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 1099511628211L
        }
        return h
    }

    private fun deterministicPriceGbp(originCode: String, destKey: String, min: Int, max: Int): Int {
        val seed = stableHash64("$priceSalt|$originCode|$destKey")
        val rng = Random(seed)
        return rng.nextInt(min, max + 1)
    }

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

    fun cardsForOrigin(originCode: String): List<OfferCard> {
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
            val km = distanceKm(originCode, effective.key) ?: return@mapNotNull null
            val (min, max) = priceBoundsGbp(km)
            OfferCard(
                destinationKey = effective.key,
                destinationName = effective.displayName,
                bookAirport = bookAirportForDestinationKey(effective.key),
                priceGbp = deterministicPriceGbp(originCode, effective.key, min, max),
                imageUrls = effective.imageUrls,
            )
        }
    }
}
