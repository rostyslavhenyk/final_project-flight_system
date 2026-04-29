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

object Flights : Table("flights") {
    private const val TIME_LENGTH = 32
    private const val STATUS_LENGTH = 32

    val id = integer("id").autoIncrement()

    val routeId = integer("routeID").references(Routes.id)

    val departureTime = varchar("departureTime", TIME_LENGTH)
    val arrivalTime = varchar("arrivalTime", TIME_LENGTH)

    val price = double("price")

    val status = varchar("status", STATUS_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

data class Flight(
    val flightID: Int,
    val routeID: Int,
    val departureTime: String,
    val arrivalTime: String,
    val price: Double,
    val status: String,
)

object FlightRepository {
    private fun ResultRow.toFlight(): Flight =
        Flight(
            flightID = this[Flights.id],
            routeID = this[Flights.routeId],
            departureTime = this[Flights.departureTime],
            arrivalTime = this[Flights.arrivalTime],
            price = this[Flights.price],
            status = this[Flights.status],
        )

    fun all(): List<Flight> =
        transaction {
            Flights.selectAll().map { it.toFlight() }
        }

    fun get(id: Int): Flight? =
        transaction {
            Flights
                .selectAll()
                .where { Flights.id eq id }
                .map { it.toFlight() }
                .singleOrNull()
        }

    fun add(
        routeID: Int,
        departureTime: String,
        arrivalTime: String,
        price: Double,
        status: String,
    ): Flight =
        transaction {
            val id =
                Flights.insert {
                    it[Flights.routeId] = routeID
                    it[Flights.departureTime] = departureTime
                    it[Flights.arrivalTime] = arrivalTime
                    it[Flights.price] = price
                    it[Flights.status] = status
                } get Flights.id

            Flight(id, routeID, departureTime, arrivalTime, price, status)
        }

    fun delete(id: Int): Boolean =
        transaction {
            Flights.deleteWhere { Flights.id eq id } > 0
        }

    // to correctly take out the data for a specific flight:
    fun allFull(): List<FlightFull> =
        transaction {
            val departureAirport = Airports.alias("departure")
            val arrivalAirport = Airports.alias("arrival")

            (
                Flights
                    .innerJoin(Routes)
                    .innerJoin(departureAirport, { Routes.departureAirportId }, { departureAirport[Airports.id] })
                    .innerJoin(arrivalAirport, { Routes.arrivalAirportId }, { arrivalAirport[Airports.id] })
            ).selectAll()
                .map {
                    val flight = it.toFlight()

                    val route =
                        Route(
                            routeID = it[Routes.id],
                            departureAirportID = it[Routes.departureAirportId],
                            arrivalAirportID = it[Routes.arrivalAirportId],
                        )

                    val departure =
                        Airport(
                            airportID = it[departureAirport[Airports.id]],
                            countryID = it[departureAirport[Airports.countryId]],
                            city = it[departureAirport[Airports.city]],
                            name = it[departureAirport[Airports.name]],
                            code = it[departureAirport[Airports.code]],
                        )

                    val arrival =
                        Airport(
                            airportID = it[arrivalAirport[Airports.id]],
                            countryID = it[arrivalAirport[Airports.countryId]],
                            city = it[arrivalAirport[Airports.city]],
                            name = it[arrivalAirport[Airports.name]],
                            code = it[arrivalAirport[Airports.code]],
                        )

                    FlightFull(
                        flight = flight,
                        route = route,
                        departureAirport = departure,
                        arrivalAirport = arrival,
                    )
                }
        }
}

data class FlightFull(
    val flight: Flight,
    val route: Route,
    val departureAirport: Airport,
    val arrivalAirport: Airport,
)
