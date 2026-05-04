package data.flight

import data.AirportRepository
import data.FlightRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Max digits after `GA` for status search, suggest API, and query parsing (flight row ids can exceed four digits). */
internal const val FLIGHT_NUMBER_INPUT_MAX_DIGITS = 6

/**
 * Flight search + status + autocomplete. **Grep:** `FLIGHT-SYSTEM-TWEAKS`
 *
 * Summary: single [FlightRepository.allFull] per flight search; one allFull for multi-day status; suggest uses
 * 7-day departure window + SQL id prefix (with padded-digit filter) or a leading-`0` scan; lighter flight-number
 * suggest vs loading all rows; homepage airport snapshot; example digits for status form.
 */
object FlightSearchRepository {
    private const val MAX_SEARCH_PAGE_SIZE = 50
    private const val DEFAULT_OFFER_LIMIT = 8
    private const val SUGGEST_LEADING_ZERO_ID_SCAN_CAP = 20_000
    private const val SUGGEST_PREFIX_FETCH_MULTIPLIER = 50
    private const val SUGGEST_PREFIX_FETCH_CAP = 500

    enum class FlightSortOption {
        Recommended,
        DepartureTime,
        ArrivalTime,
        Duration,
        Fare,
        Stops,
    }

    data class FlightScheduleRecord(
        val originCode: String,
        val destCode: String,
        val departDate: LocalDate,
        val departTime: LocalTime,
        val arrivalTime: LocalTime,
        val arrivalOffsetDays: Int,
        val durationMinutes: Int,
        val stops: Int,
        val legDepartureTimes: List<LocalTime>,
        val legArrivalTimes: List<LocalTime>,
        val legArrivalOffsetDays: List<Int>,
        val legFlightNumbers: List<String>,
        val priceLight: BigDecimal,
        val priceEssential: BigDecimal,
        val priceFlex: BigDecimal,
        val recommendedRank: Int,
        val stopoverCodes: List<String>,
        val stopoverLayoverMinutes: List<Int>,
    )

    data class PagedResult(
        val rows: List<FlightScheduleRecord>,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int,
        val pageCount: Int,
    )

    data class FlightStatusRecord(
        val flightNumber: String,
        val originCode: String,
        val destCode: String,
        val departTime: LocalTime,
        val arrivalTime: LocalTime,
        val departureOffsetDays: Int,
        val arrivalOffsetDays: Int,
        val cycleDay: Int,
        val available: Boolean,
        val statusCode: String,
        val estimatedDepartTime: LocalTime? = null,
        val estimatedArrivalTime: LocalTime? = null,
        val estimatedDepartureOffsetDays: Int? = null,
        val estimatedArrivalOffsetDays: Int? = null,
        val stopoverAirportCodes: List<String> = emptyList(),
    )

    data class OfferDestination(
        val destinationCode: String,
        val destinationCity: String,
        val lowestPrice: BigDecimal,
    )

    /*
     * Codes used to speed up loading time: one airport query per homepage request; templates pass this
     * snapshot around instead of calling [airportLabels] / [cityForCode] (each would hit the DB again).
     */

    /**
     * In-memory airport list + IATA → city label. Built by [homepageAirportsSnapshot] so one request can
     * reuse the same rows instead of calling [airportLabels] and [cityForCode] (each hits the DB again).
     */
    data class HomepageAirportsSnapshot(
        val dropdownLabels: List<String>,
        private val cityByIata: Map<String, String>,
    ) {
        fun cityForCode(code: String?): String {
            if (code.isNullOrBlank()) return "Unknown city"
            val key = code.uppercase(Locale.UK)
            return cityByIata[key] ?: key
        }
    }

    /**
     * Single DB read for homepage: dropdown strings plus city lookup. Prefer this over mixing [airportLabels]
     * and [cityForCode] on the same request (each used to re-query all airports).
     */
    fun homepageAirportsSnapshot(): HomepageAirportsSnapshot {
        val airports = AirportRepository.all()
        val labels =
            airports
                .map { airport ->
                    val city = airport.city.ifBlank { airport.name }
                    "$city (${airport.code.uppercase(Locale.UK)})"
                }.distinct()
                .sorted()
                .ifEmpty { fallbackAirports }
        val cityByIata =
            airports.associate { airport ->
                val key = airport.code.uppercase(Locale.UK)
                val cityOrCode = airport.city.ifBlank { airport.code.uppercase(Locale.UK) }
                key to cityOrCode
            }
        return HomepageAirportsSnapshot(dropdownLabels = labels, cityByIata = cityByIata)
    }

    private val fallbackAirports =
        listOf(
            "Manchester (MAN)",
            "London Heathrow (LHR)",
            "Paris (CDG)",
            "Amsterdam (AMS)",
            "Barcelona (BCN)",
            "Rome (FCO)",
            "Hong Kong (HKG)",
            "Dubai (DXB)",
            "New York (JFK)",
        )

    fun airportLabels(): List<String> =
        AirportRepository
            .all()
            .map { airport ->
                val city = airport.city.ifBlank { airport.name }
                "$city (${airport.code.uppercase(Locale.UK)})"
            }.distinct()
            .sorted()
            .ifEmpty { fallbackAirports }

    fun resolveAirportCode(raw: String): String? = AirportCodeResolver.resolve(raw)

    fun cityForCode(code: String?): String {
        if (code.isNullOrBlank()) return "Unknown city"
        return AirportRepository
            .all()
            .firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?.city
            ?.ifBlank { code.uppercase(Locale.UK) }
            ?: code.uppercase(Locale.UK)
    }

    fun airportNameForCode(code: String?): String {
        if (code.isNullOrBlank()) return "Unknown airport"
        return AirportRepository
            .all()
            .firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?.name
            ?.ifBlank { cityForCode(code) }
            ?: code.uppercase(Locale.UK)
    }

    fun search(
        originCode: String,
        destCode: String,
        depart: LocalDate,
        sort: FlightSortOption,
        ascending: Boolean,
        page: Int,
        pageSize: Int,
    ): PagedResult {
        /*
         * FLIGHT-SYSTEM-TWEAKS + codes used to speed up loading time: one [allFull] read for this search; direct rows
         * + connection builder both reuse [allFlights] (previously each path queried the DB separately).
         */
        val allFlights = FlightRepository.allFull()
        val allRowsForDate = FlightRecordMapper.recordsForDate(depart, allFlights)
        val rows =
            (
                allRowsForDate.filter {
                    it.originCode.equals(originCode, ignoreCase = true) &&
                        it.destCode.equals(destCode, ignoreCase = true)
                } +
                    FlightConnectionBuilder.recordsForDate(
                        originCode,
                        destCode,
                        depart,
                        allFlights,
                    )
            ).let { FlightSearchSorter.sortRecords(it, sort, ascending) }

        val safePageSize = pageSize.coerceIn(1, MAX_SEARCH_PAGE_SIZE)
        val pageCount = maxOf(1, (rows.size + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(1, pageCount)
        val slice = rows.drop((safePage - 1) * safePageSize).take(safePageSize)
        return PagedResult(slice, rows.size, safePage, safePageSize, pageCount)
    }

    // FLIGHT-SYSTEM-TWEAKS: one allFull for all dates (not one DB read per day).
    fun statusByFlightNumberAcrossDates(
        flightNumberRaw: String,
        dates: List<LocalDate>,
    ): List<Pair<LocalDate, FlightStatusRecord>> {
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

    fun statusByFlightNumber(
        flightNumberRaw: String,
        date: LocalDate,
    ): List<FlightStatusRecord> = statusByFlightNumberAcrossDates(flightNumberRaw, listOf(date)).map { it.second }

    // FLIGHT-SYSTEM-TWEAKS: one allFull for all dates (not one DB read per day).
    fun statusByRouteAcrossDates(
        originCode: String,
        destCode: String,
        dates: List<LocalDate>,
    ): List<Pair<LocalDate, FlightStatusRecord>> {
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

    fun statusByRoute(
        originCode: String,
        destCode: String,
        date: LocalDate,
    ): List<FlightStatusRecord> = statusByRouteAcrossDates(originCode, destCode, listOf(date)).map { it.second }

    // FLIGHT-SYSTEM-TWEAKS: status flight-number autocomplete (7-day window, GA padding, leading-zero branch).
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
                suggestDigitsAllZeroPrefix(prefix, limit, today, lastDay)
            else ->
                suggestDigitsNonZeroSqlPrefix(prefix, limit, today, lastDay)
        }
    }

    private fun suggestDigitsAllZeroPrefix(
        prefix: String,
        limit: Int,
        today: LocalDate,
        lastDay: LocalDate,
    ): List<String> =
        FlightRepository
            .idsDepartingOrderedInDateWindow(today, lastDay, maxRows = SUGGEST_LEADING_ZERO_ID_SCAN_CAP)
            .asSequence()
            .map { id -> FlightRecordMapper.flightNumberFor(id).removePrefix("GA") }
            .filter { digits -> digits.startsWith(prefix) }
            .distinct()
            .sorted()
            .take(limit)
            .toList()

    private fun suggestDigitsNonZeroSqlPrefix(
        prefix: String,
        limit: Int,
        today: LocalDate,
        lastDay: LocalDate,
    ): List<String> {
        val sqlDigitPrefix = prefix.trimStart('0').ifEmpty { "0" }
        val fetchCap =
            (limit * SUGGEST_PREFIX_FETCH_MULTIPLIER).coerceAtMost(SUGGEST_PREFIX_FETCH_CAP)
        return FlightRepository
            .idsWithFlightNumberDigitPrefixDepartingBetween(sqlDigitPrefix, fetchCap, today, lastDay)
            .asSequence()
            .map { id -> FlightRecordMapper.flightNumberFor(id).removePrefix("GA") }
            .filter { digits -> digits.startsWith(prefix) }
            .distinct()
            .sorted()
            .take(limit)
            .toList()
    }

    /** FLIGHT-SYSTEM-TWEAKS: example digits for status form (real id in the upcoming window). */
    fun flightNumberDigitsExampleForUpcomingWeek(): String {
        val today = LocalDate.now()
        val lastDay = today.plusDays((FLIGHT_STATUS_UPCOMING_DAY_COUNT - 1).toLong())
        val id = FlightRepository.minFlightIdDepartingBetween(today, lastDay) ?: return "1"
        return FlightRecordMapper.flightNumberFor(id).removePrefix("GA")
    }

    fun latestOfferDestinations(
        originCode: String,
        depart: LocalDate,
        limit: Int = DEFAULT_OFFER_LIMIT,
    ): List<OfferDestination> =
        FlightRecordMapper
            .recordsForDate(depart)
            .asSequence()
            .filter { record -> record.originCode.equals(originCode, ignoreCase = true) }
            .toList()
            .groupBy { record -> record.destCode.uppercase(Locale.UK) }
            .map { (destinationCode, records) ->
                OfferDestination(
                    destinationCode = destinationCode,
                    destinationCity = cityForCode(destinationCode),
                    lowestPrice = records.minOf { record -> record.priceLight },
                )
            }.sortedWith(compareBy({ it.lowestPrice }, { it.destinationCity }))
            .take(limit.coerceAtLeast(1))

    /**
     * Cheapest Economy Light fare for a single non-stop origin → destination on [depart], or null if none.
     *
     * Loads all flights for that day each time it is called. For several destinations from the same origin
     * (e.g. homepage cards), use [lowestLightFaresByDestinationForOrigin] once instead.
     */
    fun lowestLightFare(
        originCode: String,
        destCode: String,
        depart: LocalDate,
    ): BigDecimal? =
        FlightRecordMapper
            .recordsForDate(depart)
            .asSequence()
            .filter { row ->
                row.originCode.equals(originCode, ignoreCase = true) &&
                    row.destCode.equals(destCode, ignoreCase = true)
            }.minOfOrNull { row -> row.priceLight }

    /*
     * FLIGHT-SYSTEM-TWEAKS + codes used to speed up loading time: one flight load for many destinations (e.g. homepage
     * cards), instead of calling [lowestLightFare] per card (each used to reload the full schedule).
     */

    /**
     * For one [originCode] and calendar day [depart], returns the cheapest Light fare per destination airport
     * (non-stop rows only), using a **single** flight load — same data as repeated [lowestLightFare] calls,
     * without re-querying the whole schedule for each destination.
     */
    fun lowestLightFaresByDestinationForOrigin(
        originCode: String,
        depart: LocalDate,
    ): Map<String, BigDecimal> {
        val originKey = originCode.uppercase(Locale.UK)
        return FlightRecordMapper
            .recordsForDate(depart)
            .asSequence()
            .filter { row -> row.originCode.equals(originKey, ignoreCase = true) }
            .groupBy { row -> row.destCode.uppercase(Locale.UK) }
            .mapValues { (_, rows) -> rows.minOf { it.priceLight } }
    }
}
