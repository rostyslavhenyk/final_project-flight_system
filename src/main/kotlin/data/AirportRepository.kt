package data

import java.io.File
import java.sql.DriverManager

/**
 * Loads airport names for homepage autocomplete from SQLite only: `data/db/airports.db`.
 */
object AirportRepository {
    private val sqliteFile = File("data/db/airports.db")

    fun all(): List<String> = loadFromSqlite()

    private fun loadFromSqlite(): List<String> {
        if (!sqliteFile.exists()) return emptyList()
        val jdbcUrl = "jdbc:sqlite:${sqliteFile.path}"
        return runCatching {
            DriverManager.getConnection(jdbcUrl).use { conn ->
                conn.prepareStatement("SELECT name FROM airports ORDER BY name").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val name = rs.getString("name")?.trim().orEmpty()
                                if (name.isNotBlank()) add(name)
                            }
                        }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }
}
