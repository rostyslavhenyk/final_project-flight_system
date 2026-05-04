package data

import org.jetbrains.exposed.sql.Alias
import org.jetbrains.exposed.sql.Join
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
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

/**
 * **Grep:** `FLIGHT-SYSTEM-TWEAKS` — flight-number SQL helpers (id prefix, departure window, leading-zero scan),
 * plus normal CRUD used across search/status.
 */
object FlightRepository {
    private const val FLIGHT_NUMBER_ID_MATCH_QUERY_CAP = 100
    private const val FLIGHT_IDS_IN_DATE_WINDOW_SCAN_CAP = 25_000

    /** Width for `CAST(flights.id AS VARCHAR)` in SQLite LIKE patterns (must fit stringified ids). */
    private const val FLIGHT_ID_CAST_VARCHAR_LENGTH = 32

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

    /**
     * Flight “numbers” are `GA` + decimal [id]. Uses SQLite `CAST(id AS TEXT) LIKE` so we only read matching rows
     * (avoids loading every flight for autocomplete on large schedules).
     */
    fun idsWithFlightNumberDigitPrefix(
        prefixDigits: String,
        limit: Int,
    ): List<Int> =
        transaction {
            val digits = prefixDigits.filter { it.isDigit() }
            if (digits.isEmpty()) {
                return@transaction emptyList()
            }
            val lim = limit.coerceIn(1, FLIGHT_NUMBER_ID_MATCH_QUERY_CAP)
            val pattern = digits + "%"
            Flights
                .select(Flights.id)
                .where { Flights.id.castTo<String>(VarCharColumnType(FLIGHT_ID_CAST_VARCHAR_LENGTH)) like pattern }
                .orderBy(Flights.id, SortOrder.ASC)
                .limit(lim)
                .map { it[Flights.id] }
        }

    private fun departureLowerInclusive(firstDate: LocalDate): String =
        LocalDateTime.of(firstDate, LocalTime.MIN).toString()

    private fun departureUpperExclusiveAfterLastDay(lastInclusive: LocalDate): String =
        LocalDateTime.of(lastInclusive.plusDays(1), LocalTime.MIN).toString()

    /**
     * Like [idsWithFlightNumberDigitPrefix] but only flights whose stored [Flights.departureTime] falls on
     * [firstDateInclusive] … [lastDateInclusive] (ISO local strings sort correctly for the generator’s format).
     */
    fun idsWithFlightNumberDigitPrefixDepartingBetween(
        prefixDigits: String,
        limit: Int,
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
    ): List<Int> =
        transaction {
            val digits = prefixDigits.filter { it.isDigit() }
            if (digits.isEmpty()) {
                return@transaction emptyList()
            }
            val lim = limit.coerceIn(1, FLIGHT_NUMBER_ID_MATCH_QUERY_CAP)
            val pattern = digits + "%"
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureUpperExclusiveAfterLastDay(lastDateInclusive)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.id.castTo<String>(VarCharColumnType(FLIGHT_ID_CAST_VARCHAR_LENGTH)) like pattern) and
                        (Flights.departureTime greaterEq start) and
                        (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(lim)
                .map { it[Flights.id] }
        }

    fun minFlightIdDepartingBetween(
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
    ): Int? =
        transaction {
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureUpperExclusiveAfterLastDay(lastDateInclusive)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.departureTime greaterEq start) and (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.get(Flights.id)
        }

    /**
     * Flight ids with a departure in the window, lowest id first. Used when autocomplete cannot use
     * `CAST(id AS TEXT) LIKE '0%'` (integer text never starts with `0`).
     */
    fun idsDepartingOrderedInDateWindow(
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
        maxRows: Int,
    ): List<Int> =
        transaction {
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureUpperExclusiveAfterLastDay(lastDateInclusive)
            val lim = maxRows.coerceIn(1, FLIGHT_IDS_IN_DATE_WINDOW_SCAN_CAP)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.departureTime greaterEq start) and (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(lim)
                .map { it[Flights.id] }
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

    fun allFull(): List<FlightFull> =
        transaction {
            val fullQuery = fullFlightsQuery()

            fullQuery.join
                .selectAll()
                .orderBy(Flights.id to SortOrder.ASC)
                .map { it.toFlightFull(fullQuery) }
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
