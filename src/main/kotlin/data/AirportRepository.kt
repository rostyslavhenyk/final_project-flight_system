package data

import java.io.File

/**
 * Loads airport names from data/airports.csv.
 * CSV format: first line is header "name", then one airport per line (e.g. "Manchester (MAN)").
 * To use a real database later, replace the file read with a DB query and return the same List<String>.
 */
object AirportRepository {
    private val file = File("data/airports.csv")
    private val header = "name\n"

    fun all(): List<String> {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText(header + "Manchester (MAN)\nLondon Heathrow (LHR)\nHong Kong (HKG)\n")
        }
        return file.readLines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
