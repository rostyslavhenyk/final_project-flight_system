package data

import java.io.File

object CountryRepository {
    private val file = File("data/country.csv")
    private val countries = mutableListOf<Country>()

    init {
        file.parentFile?.mkdirs()

// If CSV doesn’t exist, create it with header only 
        if (!file.exists()) {
            file.writeText("countryID,name\n")
        }
//otherwise reads the file
        file.readLines().drop(1).forEach { line ->
            val parts = line.split(",", limit = 2)
            if (parts.size == 2) {
                val id = parts[0].toIntOrNull() ?: return@forEach
                countries.add(Country(id, parts[1]))
            }
        }
    }

    fun all(): List<Country> = countries

    fun get(id: Int): Country? = countries.find { it.countryID == id }
}