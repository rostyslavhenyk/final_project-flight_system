package data

import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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

internal fun departureLowerInclusive(firstDate: LocalDate): String =
    LocalDateTime.of(firstDate, LocalTime.MIN).toString()

internal fun departureEndExclusive(lastInclusive: LocalDate): String =
    LocalDateTime.of(lastInclusive.plusDays(1), LocalTime.MIN).toString()

/** Normal flight CRUD plus full flight joins used across search/status and staff pages. */
object FlightRepository {
    internal fun ResultRow.toFlight(): Flight =
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

    fun allFull(
        firstDateInclusive: LocalDate? = null,
        lastDateInclusive: LocalDate? = null,
        originCode: String? = null,
    ): List<FlightFull> =
        transaction {
            val fullQuery = fullFlightsQuery()
            val query = fullQuery.join.selectAll()
            val dateWindow =
                if (firstDateInclusive != null && lastDateInclusive != null) {
                    val start = departureLowerInclusive(firstDateInclusive)
                    val endExclusive = departureEndExclusive(lastDateInclusive)
                    (Flights.departureTime greaterEq start) and (Flights.departureTime less endExclusive)
                } else {
                    null
                }
            val originFilter = originCode?.let { code -> fullQuery.departureAirport[Airports.code] eq code }
            val filters = listOfNotNull(dateWindow, originFilter)
            val filteredQuery =
                filters.fold(query) { currentQuery, filter ->
                    currentQuery.andWhere { filter }
                }
            filteredQuery
                .orderBy(Flights.id to SortOrder.ASC)
                .map { it.toFlightFull(fullQuery) }
        }

    fun countBetweenDates(
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
    ): Long =
        transaction {
            Flights
                .selectAll()
                .where {
                    (Flights.departureTime greaterEq departureLowerInclusive(firstDateInclusive)) and
                        (Flights.departureTime less departureEndExclusive(lastDateInclusive))
                }.count()
        }

    fun pagedFull(
        page: Int,
        pageSize: Int,
    ): FlightPage =
        transaction {
            val total = Flights.selectAll().count()
            val pageCount = ((total + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
            val currentPage = page.coerceIn(1, pageCount)
            val offset = ((currentPage - 1) * pageSize).toLong()

            val flights =
                fullFlightsQuery().let { fullQuery ->
                    fullQuery.join
                        .selectAll()
                        .orderBy(Flights.id to SortOrder.ASC)
                        .limit(pageSize, offset = offset)
                        .map { it.toFlightFull(fullQuery) }
                }

            FlightPage(
                flights = flights,
                page = currentPage,
                pageSize = pageSize,
                total = total,
                pageCount = pageCount,
            )
        }

    private fun fullFlightsQuery(): FullFlightQuery {
        val departureAirport = Airports.alias("departure")
        val arrivalAirport = Airports.alias("arrival")

        return FullFlightQuery(
            join =
                Flights
                    .innerJoin(Routes)
                    .innerJoin(departureAirport, { Routes.departureAirportId }, { departureAirport[Airports.id] })
                    .innerJoin(arrivalAirport, { Routes.arrivalAirportId }, { arrivalAirport[Airports.id] }),
            departureAirport = departureAirport,
            arrivalAirport = arrivalAirport,
        )
    }

    private fun ResultRow.toFlightFull(fullQuery: FullFlightQuery): FlightFull {
        val departureAirport = fullQuery.departureAirport
        val arrivalAirport = fullQuery.arrivalAirport
        val flight = toFlight()

        val route =
            Route(
                routeID = this[Routes.id],
                departureAirportID = this[Routes.departureAirportId],
                arrivalAirportID = this[Routes.arrivalAirportId],
            )

        val departure =
            Airport(
                airportID = this[departureAirport[Airports.id]],
                countryID = this[departureAirport[Airports.countryId]],
                city = this[departureAirport[Airports.city]],
                name = this[departureAirport[Airports.name]],
                code = this[departureAirport[Airports.code]],
            )

        val arrival =
            Airport(
                airportID = this[arrivalAirport[Airports.id]],
                countryID = this[arrivalAirport[Airports.countryId]],
                city = this[arrivalAirport[Airports.city]],
                name = this[arrivalAirport[Airports.name]],
                code = this[arrivalAirport[Airports.code]],
            )

        return FlightFull(
            flight = flight,
            route = route,
            departureAirport = departure,
            arrivalAirport = arrival,
        )
    }

    private data class FullFlightQuery(
        val join: Join,
        val departureAirport: Alias<Airports>,
        val arrivalAirport: Alias<Airports>,
    )
}

data class FlightFull(
    val flight: Flight,
    val route: Route,
    val departureAirport: Airport,
    val arrivalAirport: Airport,
)

data class FlightPage(
    val flights: List<FlightFull>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val pageCount: Int,
)
