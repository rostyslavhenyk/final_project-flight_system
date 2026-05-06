package data.flight

import data.FlightFull
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.absoluteValue

private const val MIN_LAYOVER_MINUTES = 75
private const val MAX_LOOKAHEAD_DAYS = 2
private const val CONNECTIONS_PER_STOP_COUNT = 2
private const val ONE_STOP_HUB_LIMIT = 4
private const val TWO_STOP_PAIR_LIMIT = 3
private const val CONNECTED_FARE_MULTIPLIER = "0.82"
private const val STOPOVER_PRICE_INCREMENT = 35
private const val ESSENTIAL_FARE_INCREMENT = "45.00"
private const val FLEX_FARE_INCREMENT = "75.00"
private const val CONNECTION_RANK_BASE = 50000
private const val CONNECTION_RANK_STOP_WEIGHT = 1000
private const val CONNECTION_RANK_SPREAD = 900

internal object FlightConnectionBuilder {
    private val hubPool =
        listOf("DXB", "DOH", "IST", "SIN", "HKG", "AUH", "KUL", "DEL", "LHR", "AMS", "BKK", "FRA")

    private val twoStopPatterns =
        listOf(
            "LHR" to "BKK",
            "AMS" to "DXB",
            "IST" to "SIN",
            "CDG" to "DOH",
            "FRA" to "HKG",
            "MUC" to "SIN",
            "ZRH" to "DXB",
            "CPH" to "AMS",
            "DOH" to "BKK",
            "DXB" to "HKG",
        )

    /** Reuses the loaded flight list for connection lookahead. */
    fun recordsForDate(
        originCode: String,
        destCode: String,
        depart: LocalDate,
        allFlights: List<FlightFull>,
    ): List<FlightSearchRepository.FlightScheduleRecord> {
        val origin = originCode.uppercase(Locale.UK)
        val destination = destCode.uppercase(Locale.UK)
        val rowsByDate =
            (0..MAX_LOOKAHEAD_DAYS).associateWith { dayOffset ->
                val d = depart.plusDays(dayOffset.toLong())
                FlightRecordMapper.recordsForDate(d, allFlights)
            }
        val rowsByRoute =
            rowsByDate
                .values
                .flatten()
                .filter { row -> row.stops == 0 }
                .groupBy { row -> row.originCode.uppercase(Locale.UK) to row.destCode.uppercase(Locale.UK) }
        val oneStopRecords =
            pickOneStopHubs(origin, destination).mapNotNull { hubCode ->
                val firstLeg = rowsByRoute[origin to hubCode]?.firstLegOn(depart)
                buildConnection(
                    depart = depart,
                    legs =
                        listOfNotNull(
                            firstLeg,
                            rowsByRoute[hubCode to destination]?.firstConnectableLegAfter(firstLeg),
                        ),
                    stopoverCodes = listOf(hubCode),
                )
            }
        val twoStopRecords =
            pickTwoStopPairs(origin, destination).mapNotNull { stopovers ->
                val firstLeg = rowsByRoute[origin to stopovers.first]?.firstLegOn(depart)
                val secondLeg = rowsByRoute[stopovers.first to stopovers.second]?.firstConnectableLegAfter(firstLeg)
                val thirdLeg = rowsByRoute[stopovers.second to destination]?.firstConnectableLegAfter(secondLeg)
                buildConnection(
                    depart = depart,
                    legs = listOfNotNull(firstLeg, secondLeg, thirdLeg),
                    stopoverCodes = listOf(stopovers.first, stopovers.second),
                )
            }

        return (twoCheapest(oneStopRecords) + twoCheapest(twoStopRecords))
            .sortedWith(compareBy({ it.stops }, { it.priceLight }, { it.departTime }))
    }

    private fun List<FlightSearchRepository.FlightScheduleRecord>.firstLegOn(
        depart: LocalDate,
    ): FlightSearchRepository.FlightScheduleRecord? =
        filter { row -> row.departDate == depart }
            .sortedWith(compareBy({ it.recommendedRank }, { it.departTime }))
            .firstOrNull()

    private fun List<FlightSearchRepository.FlightScheduleRecord>.firstConnectableLegAfter(
        previousLeg: FlightSearchRepository.FlightScheduleRecord?,
    ): FlightSearchRepository.FlightScheduleRecord? {
        if (previousLeg == null) return null
        val earliestDeparture = legArrivalInstant(previousLeg) + Duration.ofMinutes(MIN_LAYOVER_MINUTES.toLong())
        return sortedWith(compareBy { legDepartureInstant(it) })
            .firstOrNull { nextLeg -> legDepartureInstant(nextLeg) >= earliestDeparture }
    }

    private fun buildConnection(
        depart: LocalDate,
        legs: List<FlightSearchRepository.FlightScheduleRecord>,
        stopoverCodes: List<String>,
    ): FlightSearchRepository.FlightScheduleRecord? {
        val expectedLegCount = stopoverCodes.size + 1
        val firstLeg = legs.first()
        val lastLeg = legs.last()
        val layovers = layoverMinutesBetween(legs)
        val durationMinutes =
            Duration.between(legDepartureInstant(firstLeg), legArrivalInstant(lastLeg)).toMinutes().toInt()

        return if (isValidConnection(legs, expectedLegCount, firstLeg, depart, durationMinutes, layovers)) {
            buildConnectedRecord(depart, legs, stopoverCodes, durationMinutes, layovers)
        } else {
            null
        }
    }

    private fun buildConnectedRecord(
        depart: LocalDate,
        legs: List<FlightSearchRepository.FlightScheduleRecord>,
        stopoverCodes: List<String>,
        durationMinutes: Int,
        layovers: List<Int>,
    ): FlightSearchRepository.FlightScheduleRecord {
        val firstLeg = legs.first()
        val lastLeg = legs.last()
        val legArrivalOffsets = legArrivalOffsets(depart, legs)
        val light = connectedLightFare(legs, stopoverCodes.size)
        val essential = (light + BigDecimal(ESSENTIAL_FARE_INCREMENT)).setScale(2, RoundingMode.HALF_UP)
        val flex = (essential + BigDecimal(FLEX_FARE_INCREMENT)).setScale(2, RoundingMode.HALF_UP)

        return FlightSearchRepository.FlightScheduleRecord(
            originCode = firstLeg.originCode,
            destCode = lastLeg.destCode,
            departDate = depart,
            departTime = firstLeg.departTime,
            arrivalTime = lastLeg.arrivalTime,
            arrivalOffsetDays = legArrivalOffsets.last().coerceAtLeast(0),
            durationMinutes = durationMinutes,
            stops = stopoverCodes.size,
            legDepartureTimes = legs.map { it.departTime },
            legArrivalTimes = legs.map { it.arrivalTime },
            legArrivalOffsetDays = legArrivalOffsets.map { it.coerceAtLeast(0) },
            legFlightNumbers = legs.flatMap { it.legFlightNumbers },
            priceLight = light,
            priceEssential = essential,
            priceFlex = flex,
            recommendedRank = connectedRecommendedRank(legs, stopoverCodes.size),
            stopoverCodes = stopoverCodes,
            stopoverLayoverMinutes = layovers,
        )
    }

    private fun layoverMinutesBetween(legs: List<FlightSearchRepository.FlightScheduleRecord>): List<Int> =
        legs.zipWithNext { previousLeg, nextLeg ->
            Duration.between(legArrivalInstant(previousLeg), legDepartureInstant(nextLeg)).toMinutes().toInt()
        }

    private fun pickOneStopHubs(
        originCode: String,
        destCode: String,
    ): List<String> =
        hubPool
            .filterNot { hubCode -> hubCode == originCode || hubCode == destCode }
            .sortedBy { hubCode -> "$originCode-$destCode-$hubCode".hashCode().absoluteValue }
            .take(ONE_STOP_HUB_LIMIT)

    private fun pickTwoStopPairs(
        originCode: String,
        destCode: String,
    ): List<Pair<String, String>> =
        twoStopPatterns
            .filter { (firstHub, secondHub) ->
                firstHub != secondHub &&
                    firstHub !in setOf(originCode, destCode) &&
                    secondHub !in setOf(originCode, destCode)
            }.sortedBy { (firstHub, secondHub) ->
                "$originCode-$destCode-$firstHub-$secondHub".hashCode().absoluteValue
            }.take(TWO_STOP_PAIR_LIMIT)

    private fun legDepartureInstant(row: FlightSearchRepository.FlightScheduleRecord): Instant =
        LocalDateTime
            .of(row.departDate, row.departTime)
            .toInstant(AirportTimeZoneResolver.offsetForIata(row.originCode))

    private fun legArrivalInstant(row: FlightSearchRepository.FlightScheduleRecord): Instant =
        LocalDateTime
            .of(row.departDate.plusDays(row.arrivalOffsetDays.toLong()), row.arrivalTime)
            .toInstant(AirportTimeZoneResolver.offsetForIata(row.destCode))
}

private fun legArrivalOffsets(
    depart: LocalDate,
    legs: List<FlightSearchRepository.FlightScheduleRecord>,
): List<Int> =
    legs.map { leg ->
        ChronoUnit.DAYS.between(depart, leg.departDate.plusDays(leg.arrivalOffsetDays.toLong())).toInt()
    }

private fun isValidConnection(
    legs: List<FlightSearchRepository.FlightScheduleRecord>,
    expectedLegCount: Int,
    firstLeg: FlightSearchRepository.FlightScheduleRecord,
    depart: LocalDate,
    durationMinutes: Int,
    layovers: List<Int>,
): Boolean {
    val legCountMatches = legs.size == expectedLegCount
    val startsOnRequestedDate = firstLeg.departDate == depart
    val hasPositiveDuration = durationMinutes > 0
    val layoversAreLongEnough = layovers.none { layoverMinutes -> layoverMinutes < MIN_LAYOVER_MINUTES }
    return legCountMatches && startsOnRequestedDate && hasPositiveDuration && layoversAreLongEnough
}

private fun connectedLightFare(
    legs: List<FlightSearchRepository.FlightScheduleRecord>,
    stopoverCount: Int,
): BigDecimal =
    legs
        .fold(BigDecimal.ZERO) { total, leg -> total + leg.priceLight }
        .multiply(BigDecimal(CONNECTED_FARE_MULTIPLIER))
        .add(BigDecimal(stopoverCount * STOPOVER_PRICE_INCREMENT))
        .setScale(2, RoundingMode.HALF_UP)

private fun connectedRecommendedRank(
    legs: List<FlightSearchRepository.FlightScheduleRecord>,
    stopoverCount: Int,
): Int =
    CONNECTION_RANK_BASE + stopoverCount * CONNECTION_RANK_STOP_WEIGHT +
        legs.joinToString("-") { it.recommendedRank.toString() }.hashCode().absoluteValue % CONNECTION_RANK_SPREAD

private fun twoCheapest(
    records: List<FlightSearchRepository.FlightScheduleRecord>,
): List<FlightSearchRepository.FlightScheduleRecord> =
    records
        .distinctBy { row -> row.legFlightNumbers.joinToString("|") }
        .sortedWith(compareBy({ it.priceLight }, { it.durationMinutes }, { it.departTime }))
        .take(CONNECTIONS_PER_STOP_COUNT)
