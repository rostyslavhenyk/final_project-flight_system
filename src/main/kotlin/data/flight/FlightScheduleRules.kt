package data.flight

import java.time.LocalTime
import java.util.Locale
import kotlin.math.absoluteValue

internal object FlightScheduleRules {
    private const val SHORT_HAUL_MINUTES = 55
    private const val SHORT_HAUL_SPREAD_MINUTES = 125
    private const val MEDIUM_HAUL_MINUTES = 260
    private const val MEDIUM_HAUL_SPREAD_MINUTES = 360
    private const val LONG_HAUL_MINUTES = 420
    private const val LONG_HAUL_SPREAD_MINUTES = 420
    private const val SHORT_HAUL_PRICE_LIMIT_MINUTES = 90
    private const val MEDIUM_HAUL_PRICE_LIMIT_MINUTES = 150
    private const val LONG_HAUL_BASE_PRICE = 399.0
    private const val PRICE_DURATION_DIVISOR = 30
    private const val SHORT_HAUL_PRICE = 89.0
    private const val MEDIUM_HAUL_PRICE = 129.0
    private const val DEFAULT_PRICE = 169.0
    private const val ROUTE_TIME_VARIANT_COUNT = 3
    private const val MORNING_HOUR_ONE = 6
    private const val MORNING_HOUR_TWO = 7
    private const val MORNING_HOUR_THREE = 8
    private const val MIDDAY_HOUR_ONE = 12
    private const val MIDDAY_HOUR_TWO = 13
    private const val MIDDAY_HOUR_THREE = 14
    private const val EVENING_HOUR_ONE = 17
    private const val EVENING_HOUR_TWO = 18
    private const val EVENING_HOUR_THREE = 19
    private const val EARLY_MORNING_MINUTE = 15
    private const val LATE_MORNING_MINUTE = 45
    private const val EARLY_MIDDAY_MINUTE = 35
    private const val LATE_MIDDAY_MINUTE = 5
    private const val EARLY_EVENING_MINUTE = 20
    private const val LATE_EVENING_MINUTE = 50

    private val morningHours = listOf(MORNING_HOUR_ONE, MORNING_HOUR_TWO, MORNING_HOUR_THREE)
    private val middayHours = listOf(MIDDAY_HOUR_ONE, MIDDAY_HOUR_TWO, MIDDAY_HOUR_THREE)
    private val eveningHours = listOf(EVENING_HOUR_ONE, EVENING_HOUR_TWO, EVENING_HOUR_THREE)

    private val europeanCodes =
        setOf("MAN", "LBA", "LHR", "LGW", "EDI", "AMS", "CDG", "BCN", "FCO", "IST", "FRA", "MUC", "ZRH", "CPH")

    private val longHaulCodes =
        setOf("DXB", "HKG", "JFK", "LAX", "YVR", "BKK", "DPS", "SIN", "NRT", "SYD", "ICN", "DOH", "AUH", "KUL", "DEL")

    fun weeklyDepartureTimes(routeIndex: Int): List<LocalTime> =
        listOf(
            LocalTime.of(
                morningHours[routeIndex % ROUTE_TIME_VARIANT_COUNT],
                if (routeIndex % 2 == 0) EARLY_MORNING_MINUTE else LATE_MORNING_MINUTE,
            ),
            LocalTime.of(
                middayHours[routeIndex % ROUTE_TIME_VARIANT_COUNT],
                if (routeIndex % 2 == 0) EARLY_MIDDAY_MINUTE else LATE_MIDDAY_MINUTE,
            ),
            LocalTime.of(
                eveningHours[routeIndex % ROUTE_TIME_VARIANT_COUNT],
                if (routeIndex % 2 == 0) EARLY_EVENING_MINUTE else LATE_EVENING_MINUTE,
            ),
        )

    fun durationForRoute(
        departureCode: String,
        arrivalCode: String,
        routeIndex: Int,
    ): Int =
        when {
            departureCode in longHaulCodes || arrivalCode in longHaulCodes ->
                LONG_HAUL_MINUTES + stableRouteNumber(departureCode, arrivalCode, routeIndex) % LONG_HAUL_SPREAD_MINUTES
            departureCode in europeanCodes && arrivalCode in europeanCodes ->
                SHORT_HAUL_MINUTES +
                    stableRouteNumber(departureCode, arrivalCode, routeIndex) % SHORT_HAUL_SPREAD_MINUTES
            else ->
                MEDIUM_HAUL_MINUTES +
                    stableRouteNumber(departureCode, arrivalCode, routeIndex) % MEDIUM_HAUL_SPREAD_MINUTES
        }

    fun priceForRoute(
        departureCode: String,
        arrivalCode: String,
        durationMinutes: Int,
    ): Double =
        when {
            departureCode in longHaulCodes || arrivalCode in longHaulCodes ->
                LONG_HAUL_BASE_PRICE + (durationMinutes / PRICE_DURATION_DIVISOR)
            durationMinutes <= SHORT_HAUL_PRICE_LIMIT_MINUTES -> SHORT_HAUL_PRICE
            durationMinutes <= MEDIUM_HAUL_PRICE_LIMIT_MINUTES -> MEDIUM_HAUL_PRICE
            else -> DEFAULT_PRICE
        }

    private fun stableRouteNumber(
        departureCode: String,
        arrivalCode: String,
        routeIndex: Int,
    ): Int = "$departureCode-$arrivalCode-$routeIndex".hashCode().absoluteValue

    /*
     * Codes used to restrict business cabin on intra-regional UK/EU routes (pair detection only; coercion
     * of `cabinClass` lives in [routes.CabinNormalization] and homepage UI).
     */

    /**
     * UK/EU-style regional pair (both endpoints in [europeanCodes]): used to hide **Business** on the homepage
     * and to coerce `cabinClass=business` to economy for search/review when the route is treated as
     * short-haul regional.
     */
    fun isIntraRegionalBusinessRestrictedPair(
        departureCode: String,
        arrivalCode: String,
    ): Boolean {
        val d = departureCode.uppercase(Locale.UK)
        val a = arrivalCode.uppercase(Locale.UK)
        return d != a && d in europeanCodes && a in europeanCodes
    }
}
