package utils

import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:sqlite:data/database.db",
            driver = "org.sqlite.JDBC",
        )
    }
}
