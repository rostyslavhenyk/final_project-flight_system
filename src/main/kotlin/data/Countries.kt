package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.transaction

object Countries : Table("countries") {
    private const val NAME_LENGTH = 128
    private const val TZ_LENGTH = 8

    val id = integer("id").autoIncrement()
    val name = varchar("name", NAME_LENGTH)
    val timeZone = varchar("timeZone", TZ_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class Country(
    val countryID: Int,
    val name: String,
    val timeZone: String,
)

object CountryRepository {
    fun all(): List<Country> =
        transaction {
            Countries.selectAll().map { it.toCountry() }
        }

    fun get(id: Int): Country? =
        transaction {
            Countries
                .selectAll()
                .where { Countries.id eq id }
                .map { it.toCountry() }
                .singleOrNull()
        }

    private fun ResultRow.toCountry(): Country =
        Country(
            countryID = this[Countries.id],
            name = this[Countries.name],
            timeZone = this[Countries.timeZone],
        )
}
