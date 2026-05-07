package data

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

private const val FLIGHT_NUMBER_ID_MATCH_QUERY_CAP = 100
private const val FLIGHT_IDS_IN_DATE_WINDOW_SCAN_CAP = 25_000

/** Width for `CAST(flights.id AS VARCHAR)` in SQLite LIKE patterns (must fit stringified ids). */
private const val FLIGHT_ID_CAST_VARCHAR_LENGTH = 32

internal object FlightNumberSqlQueries {
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
}
