package data.flight

import data.FlightFull
import data.FlightRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_MIN_DURATION_MINUTES = 1
private const val ESSENTIAL_FARE_INCREMENT = "45.00"
private const val FLEX_FARE_INCREMENT = "75.00"
private const val FLIGHT_NUMBER_DIGITS = 4

internal object FlightRecordMapper {
    fun recordsForDate(depart: LocalDate): List<FlightSearchRepository.FlightScheduleRecord> =
        recordsForDate(depart, FlightRepository.allFull())

    fun recordsForDate(
        depart: LocalDate,
        allFlights: List<FlightFull>,
    ): List<FlightSearchRepository.FlightScheduleRecord> =
        allFlights
            .asSequence()
            .filter { full -> matchesDepartureDate(full, depart) }
            .mapNotNull { full -> full.toScheduleRecord(depart) }
            .toList()

    fun statusRecord(row: FlightSearchRepository.FlightScheduleRecord): FlightSearchRepository.FlightStatusRecord =
        FlightSearchRepository.FlightStatusRecord(
            flightNumber = row.legFlightNumbers.joinToString(" / "),
            originCode = row.originCode,
            destCode = row.destCode,
            departTime = row.departTime,
            arrivalTime = row.arrivalTime,
            departureOffsetDays = 0,
            arrivalOffsetDays = row.arrivalOffsetDays,
            cycleDay = 1,
            available = true,
            statusCode = "ON_TIME",
            stopoverAirportCodes = row.stopoverCodes,
        )

    fun flightNumberFor(id: Int): String = "GA" + id.toString().padStart(FLIGHT_NUMBER_DIGITS, '0')

    private fun FlightFull.toScheduleRecord(requestedDate: LocalDate): FlightSearchRepository.FlightScheduleRecord? {
        val parsedDeparture = parseDateTimeOrTime(flight.departureTime, requestedDate)
        val parsedArrival = parseDateTimeOrTime(flight.arrivalTime, requestedDate)
        val departure = parsedDeparture
        return if (departure == null || parsedArrival == null || hasWrongRequestedDate(departure, requestedDate)) {
            null
        } else {
            buildScheduleRecord(departure, parsedArrival)
        }
    }

    private fun FlightFull.hasWrongRequestedDate(
        departure: LocalDateTime,
        requestedDate: LocalDate,
    ): Boolean = departure.toLocalDate() != requestedDate && hasDatePart(flight.departureTime)

    private fun FlightFull.buildScheduleRecord(
        departure: LocalDateTime,
        parsedArrival: LocalDateTime,
    ): FlightSearchRepository.FlightScheduleRecord {
        val normalizedArrival = normalizeArrival(parsedArrival, departure)
        val duration = durationMinutes(departure, normalizedArrival)
        val arrivalOffset = arrivalOffsetDays(departure, normalizedArrival)
        val light = BigDecimal.valueOf(flight.price).setScale(2, RoundingMode.HALF_UP)
        val essential = (light + BigDecimal(ESSENTIAL_FARE_INCREMENT)).setScale(2, RoundingMode.HALF_UP)
        val flex = (essential + BigDecimal(FLEX_FARE_INCREMENT)).setScale(2, RoundingMode.HALF_UP)

        return FlightSearchRepository.FlightScheduleRecord(
            originCode = departureAirport.code.uppercase(Locale.UK),
            destCode = arrivalAirport.code.uppercase(Locale.UK),
            departDate = departure.toLocalDate(),
            departTime = departure.toLocalTime(),
            arrivalTime = normalizedArrival.toLocalTime(),
            arrivalOffsetDays = arrivalOffset.coerceAtLeast(0),
            durationMinutes = duration,
            stops = 0,
            legDepartureTimes = listOf(departure.toLocalTime()),
            legArrivalTimes = listOf(normalizedArrival.toLocalTime()),
            legArrivalOffsetDays = listOf(arrivalOffset.coerceAtLeast(0)),
            legFlightNumbers = listOf(flightNumberFor(flight.flightID)),
            priceLight = light,
            priceEssential = essential,
            priceFlex = flex,
            recommendedRank = flight.flightID,
            stopoverCodes = emptyList(),
            stopoverLayoverMinutes = emptyList(),
        )
    }

    private fun FlightFull.normalizeArrival(
        parsedArrival: LocalDateTime,
        departure: LocalDateTime,
    ): LocalDateTime =
        if (!hasDatePart(flight.arrivalTime) && parsedArrival.isBefore(departure)) {
            parsedArrival.plusDays(1)
        } else {
            parsedArrival
        }

    private fun FlightFull.durationMinutes(
        departure: LocalDateTime,
        normalizedArrival: LocalDateTime,
    ): Int =
        Duration
            .between(
                departure.atZone(AirportTimeZoneResolver.zoneIdForIata(departureAirport.code)),
                normalizedArrival.atZone(AirportTimeZoneResolver.zoneIdForIata(arrivalAirport.code)),
            ).toMinutes()
            .toInt()
            .coerceAtLeast(DEFAULT_MIN_DURATION_MINUTES)
}

private fun arrivalOffsetDays(
    departure: LocalDateTime,
    normalizedArrival: LocalDateTime,
): Int =
    Duration
        .between(
            departure.toLocalDate().atStartOfDay(),
            normalizedArrival.toLocalDate().atStartOfDay(),
        ).toDays()
        .toInt()

private fun parseDateTimeOrTime(
    raw: String,
    fallbackDate: LocalDate,
): LocalDateTime? {
    val value = raw.trim()
    val dateTime =
        if (value.isBlank()) {
            null
        } else {
            parseDateTime(value)
        }
    return dateTime ?: parseTime(value)?.let { time -> LocalDateTime.of(fallbackDate, time) }
}

private fun parseDateTime(value: String): LocalDateTime? =
    listOf(
        { LocalDateTime.parse(value) },
        { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) },
        { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) },
    ).firstNotNullOfOrNull { parser -> runCatching { parser() }.getOrNull() }

private fun parseTime(value: String): LocalTime? =
    listOf(
        { LocalTime.parse(value) },
        { LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm")) },
        { LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm")) },
    ).firstNotNullOfOrNull { parser -> runCatching { parser() }.getOrNull() }

private fun hasDatePart(raw: String): Boolean = raw.any { it == '-' || it == '/' }

private fun matchesDepartureDate(
    full: FlightFull,
    requestedDate: LocalDate,
): Boolean {
    val departure = full.flight.departureTime.trim()
    return !hasDatePart(departure) || departure.startsWith(requestedDate.toString())
}
