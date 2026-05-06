package data.flight

import data.AirportRepository
import data.FlightRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Max digits after `GA` for status search, suggest API, and query parsing (flight row ids can exceed four digits). */
internal const val FLIGHT_NUMBER_INPUT_MAX_DIGITS = 6

/** Number of calendar days ahead of today included in flight status searches and suggestions. */
const val FLIGHT_STATUS_UPCOMING_DAY_COUNT = 7

object FlightSearchRepository {
    private const val MAX_SEARCH_PAGE_SIZE = 50
    private const val DEFAULT_OFFER_LIMIT = 8
    private const val SEARCH_CONNECTION_LOOKAHEAD_DAYS = 2
    private val airportLookup: AirportLookup by lazy {
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
        val nameByIata =
            airports.associate { airport ->
                val key = airport.code.uppercase(Locale.UK)
                val nameOrCity = airport.name.ifBlank { cityByIata[key].orEmpty().ifBlank { key } }
                key to nameOrCity
            }
        AirportLookup(labels, cityByIata, nameByIata)
    }

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

    /** Airport labels and city names loaded once for the homepage. */
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

    fun homepageAirportsSnapshot(): HomepageAirportsSnapshot {
        val lookup = airportLookup
        return HomepageAirportsSnapshot(dropdownLabels = lookup.dropdownLabels, cityByIata = lookup.cityByIata)
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
        val key = code.uppercase(Locale.UK)
        return airportLookup.cityByIata[key] ?: key
    }

    fun airportNameForCode(code: String?): String {
        if (code.isNullOrBlank()) return "Unknown airport"
        val key = code.uppercase(Locale.UK)
        return airportLookup.nameByIata[key] ?: key
    }

    fun search(
        originCode: String,
        destCode: String,
        depart: LocalDate,
        sort: FlightSortOption,
        ascending: Boolean,
        stopFilter: Int?,
        page: Int,
        pageSize: Int,
    ): PagedResult {
        val allFlights =
            FlightRepository.allFull(
                firstDateInclusive = depart,
                lastDateInclusive = depart.plusDays(SEARCH_CONNECTION_LOOKAHEAD_DAYS.toLong()),
            )
        val allRowsForDate = FlightRecordMapper.recordsForDate(depart, allFlights)
        val matchingRows =
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
            )
        val rows =
            matchingRows
                .filter { row -> stopFilter == null || row.stops == stopFilter }
                .let { FlightSearchSorter.sortRecords(it, sort, ascending) }

        val safePageSize = pageSize.coerceIn(1, MAX_SEARCH_PAGE_SIZE)
        val pageCount = maxOf(1, (rows.size + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(1, pageCount)
        val slice = rows.drop((safePage - 1) * safePageSize).take(safePageSize)
        return PagedResult(slice, rows.size, safePage, safePageSize, pageCount)
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

    /** Cheapest Economy Light fare for one non-stop route. */
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

    /** Cheapest Economy Light fare per destination from one origin/date. */
    fun lowestLightFaresByDestinationForOrigin(
        originCode: String,
        depart: LocalDate,
    ): Map<String, BigDecimal> {
        val originKey = originCode.uppercase(Locale.UK)
        val flights =
            FlightRepository.allFull(
                firstDateInclusive = depart,
                lastDateInclusive = depart,
                originCode = originKey,
            )
        return FlightRecordMapper
            .recordsForDate(depart, flights)
            .asSequence()
            .filter { row -> row.originCode.equals(originKey, ignoreCase = true) }
            .groupBy { row -> row.destCode.uppercase(Locale.UK) }
            .mapValues { (_, rows) -> rows.minOf { it.priceLight } }
    }
}

private data class AirportLookup(
    val dropdownLabels: List<String>,
    val cityByIata: Map<String, String>,
    val nameByIata: Map<String, String>,
)
