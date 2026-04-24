package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
    private fun ResultRow.toRoute(): Route =
        Route(
            routeID = this[Routes.id],
            departureAirportID = this[Routes.departureAirportId],
            arrivalAirportID = this[Routes.arrivalAirportId],
        )

    fun all(): List<Route> =
        transaction {
            Routes.selectAll().map { it.toRoute() }
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
