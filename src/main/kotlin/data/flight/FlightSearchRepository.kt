package data.flight

import data.AirportRepository
import data.FlightRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

object FlightSearchRepository {
    private const val MAX_SEARCH_PAGE_SIZE = 50
    private const val FLIGHT_NUMBER_PREFIX_LENGTH = 4
    private const val DEFAULT_OFFER_LIMIT = 8

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
        val allRowsForDate = FlightRecordMapper.recordsForDate(depart)
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
                        FlightRecordMapper::recordsForDate,
                    )
            ).let { FlightSearchSorter.sortRecords(it, sort, ascending) }

        val safePageSize = pageSize.coerceIn(1, MAX_SEARCH_PAGE_SIZE)
        val pageCount = maxOf(1, (rows.size + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(1, pageCount)
        val slice = rows.drop((safePage - 1) * safePageSize).take(safePageSize)
        return PagedResult(slice, rows.size, safePage, safePageSize, pageCount)
    }

    fun statusByFlightNumber(
        flightNumberRaw: String,
        date: LocalDate,
    ): List<FlightStatusRecord> {
        val needle = flightNumberRaw.trim().uppercase(Locale.UK)
        if (needle.isBlank()) return emptyList()
        return FlightRecordMapper
            .recordsForDate(date)
            .filter { it.legFlightNumbers.any { flightNo -> flightNo.equals(needle, ignoreCase = true) } }
            .map { FlightRecordMapper.statusRecord(it) }
    }

    fun statusByRoute(
        originCode: String,
        destCode: String,
        date: LocalDate,
    ): List<FlightStatusRecord> =
        FlightRecordMapper
            .recordsForDate(date)
            .filter {
                it.originCode.equals(originCode, ignoreCase = true) &&
                    it.destCode.equals(destCode, ignoreCase = true)
            }.map { FlightRecordMapper.statusRecord(it) }

    fun suggestFlightNumberDigits(
        rawDigits: String,
        limit: Int = 20,
    ): List<String> {
        val prefix = rawDigits.filter { it.isDigit() }.take(FLIGHT_NUMBER_PREFIX_LENGTH)
        if (prefix.isBlank()) return emptyList()
        return FlightRepository
            .allFull()
            .asSequence()
            .map { FlightRecordMapper.flightNumberFor(it.flight.flightID).removePrefix("GA") }
            .filter { it.startsWith(prefix) }
            .distinct()
            .sorted()
            .take(limit)
            .toList()
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
}
