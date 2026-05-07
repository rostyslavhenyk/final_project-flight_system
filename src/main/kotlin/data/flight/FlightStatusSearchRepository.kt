package data.flight

import data.FlightRepository
import java.time.LocalDate
import java.util.Locale

object FlightStatusSearchRepository {
    fun statusByFlightNumberAcrossDates(
        flightNumberRaw: String,
        dates: List<LocalDate>,
    ): List<Pair<LocalDate, FlightSearchRepository.FlightStatusRecord>> {
        val needle = flightNumberRaw.trim().uppercase(Locale.UK)
        if (needle.isBlank() || dates.isEmpty()) return emptyList()
        val allFlights = FlightRepository.allFull()
        return dates.flatMap { searchDate ->
            FlightRecordMapper
                .recordsForDate(searchDate, allFlights)
                .filter { row ->
                    row.legFlightNumbers.any { flightNo -> flightNo.equals(needle, ignoreCase = true) }
                }.map { searchDate to FlightRecordMapper.statusRecord(it) }
        }
    }

    fun statusByRouteAcrossDates(
        originCode: String,
        destCode: String,
        dates: List<LocalDate>,
    ): List<Pair<LocalDate, FlightSearchRepository.FlightStatusRecord>> {
        if (dates.isEmpty()) return emptyList()
        val allFlights = FlightRepository.allFull()
        return dates.flatMap { searchDate ->
            FlightRecordMapper
                .recordsForDate(searchDate, allFlights)
                .filter {
                    it.originCode.equals(originCode, ignoreCase = true) &&
                        it.destCode.equals(destCode, ignoreCase = true)
                }.map { searchDate to FlightRecordMapper.statusRecord(it) }
        }
    }

    fun suggestFlightNumberDigits(
        rawDigits: String,
        limit: Int = 20,
    ): List<String> {
        val prefix = rawDigits.filter { it.isDigit() }.take(FLIGHT_NUMBER_INPUT_MAX_DIGITS)
        val today = LocalDate.now()
        val lastDay = today.plusDays((FLIGHT_STATUS_UPCOMING_DAY_COUNT - 1).toLong())
        return when {
            prefix.isBlank() -> emptyList()
            prefix.all { it == '0' } ->
                suggestZeroPrefix(prefix, limit, today, lastDay)
            else ->
                suggestSqlPrefix(prefix, limit, today, lastDay)
        }
    }

    fun flightNumberDigitsExampleForUpcomingWeek(): String {
        val today = LocalDate.now()
        val lastDay = today.plusDays((FLIGHT_STATUS_UPCOMING_DAY_COUNT - 1).toLong())
        val id = FlightNumberQueries.minIdBetween(today, lastDay) ?: return "1"
        return FlightRecordMapper.flightNumberFor(id).removePrefix("GA")
    }

    private fun suggestZeroPrefix(
        prefix: String,
        limit: Int,
        today: LocalDate,
        lastDay: LocalDate,
    ): List<String> =
        FlightNumberQueries
            .idsInDateWindow(today, lastDay, maxRows = SUGGEST_LEADING_ZERO_ID_SCAN_CAP)
            .asSequence()
            .map { id -> FlightRecordMapper.flightNumberFor(id).removePrefix("GA") }
            .filter { digits -> digits.startsWith(prefix) }
            .distinct()
            .sorted()
            .take(limit)
            .toList()

    private fun suggestSqlPrefix(
        prefix: String,
        limit: Int,
        today: LocalDate,
        lastDay: LocalDate,
    ): List<String> {
        val sqlDigitPrefix = prefix.trimStart('0').ifEmpty { "0" }
        val fetchCap =
            (limit * SUGGEST_PREFIX_FETCH_MULTIPLIER).coerceAtMost(SUGGEST_PREFIX_FETCH_CAP)
        return FlightNumberQueries
            .idsWithDigitPrefix(sqlDigitPrefix, fetchCap, today, lastDay)
            .asSequence()
            .map { id -> FlightRecordMapper.flightNumberFor(id).removePrefix("GA") }
            .filter { digits -> digits.startsWith(prefix) }
            .distinct()
            .sorted()
            .take(limit)
            .toList()
    }
}

private const val SUGGEST_LEADING_ZERO_ID_SCAN_CAP = 20_000
private const val SUGGEST_PREFIX_FETCH_MULTIPLIER = 50
private const val SUGGEST_PREFIX_FETCH_CAP = 500
