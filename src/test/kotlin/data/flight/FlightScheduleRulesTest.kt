package data.flight

import testsupport.withClue
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightScheduleRulesTest {
    @Test
    fun `weeklyDepartureTimes gives deterministic primary and extra services`() {
        withClue("even route indexes get morning and evening services") {
            assertEquals(listOf(LocalTime.of(6, 15), LocalTime.of(17, 20)), FlightScheduleRules.weeklyDepartureTimes(0))
        }
        withClue("odd route indexes get the primary service only") {
            assertEquals(listOf(LocalTime.of(7, 45)), FlightScheduleRules.weeklyDepartureTimes(1))
        }
    }

    @Test
    fun `business is restricted only for different airports inside the regional network`() {
        withClue("European regional pair is restricted") {
            assertTrue(FlightScheduleRules.isIntraRegionalBusinessRestrictedPair("MAN", "AMS"))
        }
        withClue("same airport is not treated as a sellable restricted route") {
            assertFalse(FlightScheduleRules.isIntraRegionalBusinessRestrictedPair("MAN", "MAN"))
        }
        withClue("long haul route can offer business") {
            assertFalse(FlightScheduleRules.isIntraRegionalBusinessRestrictedPair("MAN", "JFK"))
        }
    }

    @Test
    fun `duration and prices stay inside expected route bands`() {
        val regionalDuration = FlightScheduleRules.durationForRoute("MAN", "LHR", 0)
        val longHaulDuration = FlightScheduleRules.durationForRoute("MAN", "JFK", 0)

        withClue("regional routes are shorter than long haul routes") {
            assertTrue(regionalDuration < longHaulDuration)
        }
        withClue("minimum duration guard prevents impossible short flights") {
            assertTrue(regionalDuration >= MINIMUM_FLIGHT_DURATION_MINUTES)
        }
        withClue("regional price uses the regional band") {
            assertEquals(REGIONAL_PRICE, FlightScheduleRules.priceForRoute("MAN", "LHR", regionalDuration))
        }
        withClue("long haul price is higher than standard short connection pricing") {
            assertTrue(FlightScheduleRules.priceForRoute("MAN", "JFK", longHaulDuration) > STANDARD_SHORT_PRICE)
        }
    }
}

private const val MINIMUM_FLIGHT_DURATION_MINUTES = 45
private const val REGIONAL_PRICE = 59.25
private const val STANDARD_SHORT_PRICE = 111.75
