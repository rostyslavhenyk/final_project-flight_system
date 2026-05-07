package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction

object Airports : Table("airports") {
    private const val CITY_LENGTH = 128
    private const val NAME_LENGTH = 256
    private const val CODE_LENGTH = 10

    val id = integer("id").autoIncrement()
    val countryId = integer("countryID").references(Countries.id)
    val city = varchar("city", CITY_LENGTH)
    val name = varchar("name", NAME_LENGTH)
    val code = varchar("code", CODE_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class Airport(
    val airportID: Int,
    val countryID: Int,
    val city: String,
    val name: String,
    val code: String,
)

object AirportRepository {
    internal fun ResultRow.toAirport() =
        Airport(
            airportID = this[Airports.id],
            countryID = this[Airports.countryId],
            city = this[Airports.city],
            name = this[Airports.name],
            code = this[Airports.code],
        )

    fun all(): List<Airport> =
        transaction {
            Airports.selectAll().map { it.toAirport() }
        }

    fun allFull(): List<AirportFull> =
        transaction {
            Airports
                .innerJoin(Countries, { Airports.countryId }, { Countries.id })
                .selectAll()
                .map {
                    AirportFull(
                        airport = it.toAirport(),
                        country = CountryRepository.run { it.toCountry() },
                    )
                }
        }

    fun get(id: Int): Airport? =
        transaction {
            Airports
                .selectAll()
                .where { Airports.id eq id }
                .map { it.toAirport() }
                .singleOrNull()
        }

    fun add(
        countryID: Int,
        city: String,
        name: String,
        code: String,
    ): Airport =
        transaction {
            val id =
                Airports.insert {
                    it[countryId] = countryID
                    it[Airports.city] = city
                    it[Airports.name] = name
                    it[Airports.code] = code
                } get Airports.id

            Airport(id, countryID, city, name, code)
        }

    fun delete(id: Int): Boolean =
        transaction {
            Airports.deleteWhere { Airports.id eq id } > 0
        }
}

data class AirportFull(
    val airport: Airport,
    val country: Country,
)
