package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction

object Routes : Table("routes") {
    val id = integer("id").autoIncrement()

    val departureAirportId = integer("departureAirportID").references(Airports.id)
    val arrivalAirportId = integer("arrivalAirportID").references(Airports.id)

    override val primaryKey = PrimaryKey(id)
}

data class Route(
    val routeID: Int,
    val departureAirportID: Int,
    val arrivalAirportID: Int,
)

object RouteRepository {
    internal fun ResultRow.toRoute(): Route =
        Route(
            routeID = this[Routes.id],
            departureAirportID = this[Routes.departureAirportId],
            arrivalAirportID = this[Routes.arrivalAirportId],
        )

    fun all(): List<Route> =
        transaction {
            Routes.selectAll().map { it.toRoute() }
        }

    fun allFull(): List<RouteFull> =
        transaction {
            val departureAirport = Airports.alias("departure")
            val arrivalAirport = Airports.alias("arrival")

            (
                Routes
                    .innerJoin(departureAirport, { Routes.departureAirportId }, { departureAirport[Airports.id] })
                    .innerJoin(arrivalAirport, { Routes.arrivalAirportId }, { arrivalAirport[Airports.id] })
            ).selectAll()
                .map {
                    RouteFull(
                        route = it.toRoute(),
                        departureAirport =
                            Airport(
                                airportID = it[departureAirport[Airports.id]],
                                countryID = it[departureAirport[Airports.countryId]],
                                city = it[departureAirport[Airports.city]],
                                name = it[departureAirport[Airports.name]],
                                code = it[departureAirport[Airports.code]],
                            ),
                        arrivalAirport =
                            Airport(
                                airportID = it[arrivalAirport[Airports.id]],
                                countryID = it[arrivalAirport[Airports.countryId]],
                                city = it[arrivalAirport[Airports.city]],
                                name = it[arrivalAirport[Airports.name]],
                                code = it[arrivalAirport[Airports.code]],
                            ),
                    )
                }
        }

    fun get(id: Int): Route? =
        transaction {
            Routes
                .selectAll()
                .where { Routes.id eq id }
                .map { it.toRoute() }
                .singleOrNull()
        }

    fun add(
        departureAirportID: Int,
        arrivalAirportID: Int,
    ): Route =
        transaction {
            val id =
                Routes.insert {
                    it[Routes.departureAirportId] = departureAirportID
                    it[Routes.arrivalAirportId] = arrivalAirportID
                } get Routes.id

            Route(id, departureAirportID, arrivalAirportID)
        }

    fun delete(id: Int): Boolean =
        transaction {
            Routes.deleteWhere { Routes.id eq id } > 0
        }
}

data class RouteFull(
    val route: Route,
    val departureAirport: Airport,
    val arrivalAirport: Airport,
)
