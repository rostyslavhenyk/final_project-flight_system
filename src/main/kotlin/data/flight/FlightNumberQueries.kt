package data.flight

import data.Flights
import data.departureEndExclusive
import data.departureLowerInclusive
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

private const val PREFIX_MATCH_CAP = 100
private const val DATE_WINDOW_SCAN_CAP = 25_000

private const val ID_CAST_LENGTH = 32

internal object FlightNumberQueries {
    fun idsWithDigitPrefix(
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
            val lim = limit.coerceIn(1, PREFIX_MATCH_CAP)
            val pattern = digits + "%"
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureEndExclusive(lastDateInclusive)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.id.castTo<String>(VarCharColumnType(ID_CAST_LENGTH)) like pattern) and
                        (Flights.departureTime greaterEq start) and
                        (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(lim)
                .map { it[Flights.id] }
        }

    fun minIdBetween(
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
    ): Int? =
        transaction {
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureEndExclusive(lastDateInclusive)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.departureTime greaterEq start) and (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.get(Flights.id)
        }

    fun idsInDateWindow(
        firstDateInclusive: LocalDate,
        lastDateInclusive: LocalDate,
        maxRows: Int,
    ): List<Int> =
        transaction {
            val start = departureLowerInclusive(firstDateInclusive)
            val endExclusive = departureEndExclusive(lastDateInclusive)
            val lim = maxRows.coerceIn(1, DATE_WINDOW_SCAN_CAP)
            Flights
                .select(Flights.id)
                .where {
                    (Flights.departureTime greaterEq start) and (Flights.departureTime less endExclusive)
                }.orderBy(Flights.id, SortOrder.ASC)
                .limit(lim)
                .map { it[Flights.id] }
        }
}
