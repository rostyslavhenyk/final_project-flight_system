package data.flight

import java.time.LocalTime
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object FlightScheduleRules {
    private const val EARTH_RADIUS_KM = 6_371.0
    private const val MINUTES_PER_HOUR = 60
    private const val SHORT_HAUL_DISTANCE_KM = 900.0
    private const val MEDIUM_HAUL_DISTANCE_KM = 3_700.0
    private const val SHORT_HAUL_AIR_SPEED_KMH = 650.0
    private const val MEDIUM_HAUL_AIR_SPEED_KMH = 780.0
    private const val LONG_HAUL_AIR_SPEED_KMH = 870.0
    private const val SHORT_HAUL_BLOCK_BUFFER_MINUTES = 45
    private const val MEDIUM_HAUL_BLOCK_BUFFER_MINUTES = 65
    private const val LONG_HAUL_BLOCK_BUFFER_MINUTES = 85
    private const val SHORT_HAUL_VARIATION_MINUTES = 16
    private const val MEDIUM_HAUL_VARIATION_MINUTES = 28
    private const val LONG_HAUL_VARIATION_MINUTES = 42
    private const val MIN_DURATION_MINUTES = 45
    private const val MAX_DURATION_MINUTES = 1_100
    private const val SHORT_HAUL_PRICE_LIMIT_MINUTES = 90
    private const val MEDIUM_HAUL_PRICE_LIMIT_MINUTES = 150
    private const val LONG_HAUL_STARTING_PRICE = 349.0
    private const val LONG_HAUL_DURATION_PRICE_DIVISOR = 35
    private const val REGIONAL_PRICE = 79.0
    private const val SHORT_CONNECTION_PRICE = 115.0
    private const val STANDARD_PRICE = 149.0
    private const val ROUTE_TIME_VARIANT_COUNT = 3
    private const val MORNING_HOUR_ONE = 6
    private const val MORNING_HOUR_TWO = 7
    private const val MORNING_HOUR_THREE = 8
    private const val EVENING_HOUR_ONE = 17
    private const val EVENING_HOUR_TWO = 18
    private const val EVENING_HOUR_THREE = 19
    private const val EARLY_MORNING_MINUTE = 15
    private const val LATE_MORNING_MINUTE = 45
    private const val EARLY_EVENING_MINUTE = 20
    private const val LATE_EVENING_MINUTE = 50
    private const val EXTRA_DAILY_DEPARTURE_INTERVAL = 2

    private val morningHours = listOf(MORNING_HOUR_ONE, MORNING_HOUR_TWO, MORNING_HOUR_THREE)
    private val eveningHours = listOf(EVENING_HOUR_ONE, EVENING_HOUR_TWO, EVENING_HOUR_THREE)

    private val europeanCodes =
        setOf("MAN", "LBA", "LHR", "LGW", "EDI", "AMS", "CDG", "BCN", "FCO", "IST", "FRA", "MUC", "ZRH", "CPH")

    private val airportCoordinates = FlightSeedData.airportCoordinates()

    fun weeklyDepartureTimes(routeIndex: Int): List<LocalTime> {
        val primaryDeparture =
            LocalTime.of(
                morningHours[routeIndex % ROUTE_TIME_VARIANT_COUNT],
                if (routeIndex % 2 == 0) EARLY_MORNING_MINUTE else LATE_MORNING_MINUTE,
            )
        val extraDeparture =
            LocalTime.of(
                eveningHours[routeIndex % ROUTE_TIME_VARIANT_COUNT],
                if (routeIndex % 2 == 0) EARLY_EVENING_MINUTE else LATE_EVENING_MINUTE,
            )
        return if (routeIndex % EXTRA_DAILY_DEPARTURE_INTERVAL == 0) {
            listOf(primaryDeparture, extraDeparture)
        } else {
            listOf(primaryDeparture)
        }
    }

    fun durationForRoute(
        departureCode: String,
        arrivalCode: String,
        routeIndex: Int,
    ): Int {
        val distanceKm = distanceKm(departureCode, arrivalCode)
        val profile =
            when {
                distanceKm <= SHORT_HAUL_DISTANCE_KM ->
                    DurationProfile(
                        SHORT_HAUL_AIR_SPEED_KMH,
                        SHORT_HAUL_BLOCK_BUFFER_MINUTES,
                        SHORT_HAUL_VARIATION_MINUTES,
                    )
                distanceKm <= MEDIUM_HAUL_DISTANCE_KM ->
                    DurationProfile(
                        MEDIUM_HAUL_AIR_SPEED_KMH,
                        MEDIUM_HAUL_BLOCK_BUFFER_MINUTES,
                        MEDIUM_HAUL_VARIATION_MINUTES,
                    )
                else ->
                    DurationProfile(
                        LONG_HAUL_AIR_SPEED_KMH,
                        LONG_HAUL_BLOCK_BUFFER_MINUTES,
                        LONG_HAUL_VARIATION_MINUTES,
                    )
            }
        val variation = stableRouteNumber(departureCode, arrivalCode, routeIndex) % profile.variationMinutes
        val airborneMinutes = ((distanceKm / profile.airSpeedKmh) * MINUTES_PER_HOUR).roundToInt()
        return (profile.blockBufferMinutes + airborneMinutes + variation).coerceIn(
            MIN_DURATION_MINUTES,
            MAX_DURATION_MINUTES,
        )
    }

    fun priceForRoute(
        departureCode: String,
        arrivalCode: String,
        durationMinutes: Int,
    ): Double =
        when {
            distanceKm(departureCode, arrivalCode) > MEDIUM_HAUL_DISTANCE_KM ->
                LONG_HAUL_STARTING_PRICE + (durationMinutes / LONG_HAUL_DURATION_PRICE_DIVISOR)
            durationMinutes <= SHORT_HAUL_PRICE_LIMIT_MINUTES -> REGIONAL_PRICE
            durationMinutes <= MEDIUM_HAUL_PRICE_LIMIT_MINUTES -> SHORT_CONNECTION_PRICE
            else -> STANDARD_PRICE
        }

    private fun stableRouteNumber(
        departureCode: String,
        arrivalCode: String,
        routeIndex: Int,
    ): Int = "$departureCode-$arrivalCode-$routeIndex".hashCode().absoluteValue

    private fun distanceKm(
        departureCode: String,
        arrivalCode: String,
    ): Double {
        val departure = airportCoordinates.getValue(departureCode.uppercase(Locale.UK))
        val arrival = airportCoordinates.getValue(arrivalCode.uppercase(Locale.UK))
        val latDelta = Math.toRadians(arrival.latitude - departure.latitude)
        val lonDelta = Math.toRadians(arrival.longitude - departure.longitude)
        val departureLat = Math.toRadians(departure.latitude)
        val arrivalLat = Math.toRadians(arrival.latitude)
        val a =
            sin(latDelta / 2) * sin(latDelta / 2) +
                cos(departureLat) * cos(arrivalLat) * sin(lonDelta / 2) * sin(lonDelta / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** True when Business should be unavailable for a regional route. */
    fun isIntraRegionalBusinessRestrictedPair(
        departureCode: String,
        arrivalCode: String,
    ): Boolean {
        val d = departureCode.uppercase(Locale.UK)
        val a = arrivalCode.uppercase(Locale.UK)
        return d != a && d in europeanCodes && a in europeanCodes
    }
}

private data class DurationProfile(
    val airSpeedKmh: Double,
    val blockBufferMinutes: Int,
    val variationMinutes: Int,
)
