package data

import java.io.File

/**
 * One row from data/offers.csv: origin airport code, destination name, cabin class, price in GBP.
 */
data class Offer(
    val originCode: String,
    val destinationName: String,
    val cabinClass: String,
    val priceGbp: Int,
)

/**
 * Loads offers from data/offers.csv.
 * Columns: origin_code, destination_name, cabin_class, price_gbp
 * Filter by origin (e.g. MAN, LBA) to show "from Leeds" vs "from Manchester".
 */
object OffersRepository {
    private val file = File("data/offers.csv")

    fun all(): List<Offer> = loadOffers(null)

    fun byOrigin(originCode: String): List<Offer> = loadOffers(originCode)

    private fun loadOffers(originFilter: String?): List<Offer> {
        file.parentFile?.mkdirs()
        if (!file.exists()) return emptyList()
        return file.readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(",", limit = 4)
                if (parts.size != 4) return@mapNotNull null
                val origin = parts[0].trim()
                if (originFilter != null && origin != originFilter) return@mapNotNull null
                val price = parts[3].trim().toIntOrNull() ?: return@mapNotNull null
                Offer(origin, parts[1].trim(), parts[2].trim(), price)
            }
    }
}
