package routes.flight

import data.flight.FlightSearchRepository
import testsupport.withClue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CabinAndRouteBlockTest {
    @Test
    fun `cabin normalization allows business only when every leg supports it`() {
        withClue("business is kept for long haul legs") {
            assertEquals("business", CabinNormalization.normalizedCabinForLegs(" Business ", listOf("MAN" to "JFK")))
        }
        withClue("business falls back to economy for regional legs") {
            assertEquals("economy", CabinNormalization.normalizedCabinForLegs("business", listOf("MAN" to "AMS")))
        }
        withClue("return bookings fall back if either leg is regional") {
            assertEquals(
                "economy",
                CabinNormalization.normalizedCabinForLegs("business", listOf("MAN" to "JFK", "AMS" to "MAN")),
            )
        }
        withClue("unknown cabin values are economy") {
            assertEquals("economy", CabinNormalization.normalizedCabinForLegs("premium", listOf("MAN" to "JFK")))
        }
    }

    @Test
    fun `route blocks alternate flight segments and connections`() {
        val blocks = buildRouteBlocks(oneStopRecord())

        withClue("one-stop route renders segment, connection, segment") {
            assertEquals(listOf("segment", "connect", "segment"), blocks.map { it["kind"] })
        }
        withClue("connection block uses supplied layover minutes") {
            assertEquals("1h 30 min", blocks[1]["layoverLabel"])
        }
        withClue("second segment uses the second flight number") {
            assertEquals("GA202", blocks[2]["flight"])
        }
        withClue("overnight final arrival keeps the arrival day offset") {
            assertEquals(1, blocks[2]["arrPlusDays"])
        }
    }

    @Test
    fun `fare invariants prevent package prices from going backwards`() {
        val fixed =
            enforceFareInvariants(
                mapOf(
                    "from" to BigDecimal("1.00"),
                    "light" to BigDecimal("100.00"),
                    "essential" to BigDecimal("90.00"),
                    "flex" to BigDecimal("90.00"),
                ),
                cabinRaw = "economy",
            )

        withClue("essential cannot be below light") {
            assertEquals(BigDecimal("100.00"), fixed.getValue("essential"))
        }
        withClue("flex must be above essential") {
            assertEquals(BigDecimal("101.00"), fixed.getValue("flex"))
        }
        withClue("economy from price remains light fare") {
            assertEquals(BigDecimal("100.00"), fixed.getValue("from"))
        }
    }

    private fun oneStopRecord(): FlightSearchRepository.FlightScheduleRecord =
        FlightSearchRepository.FlightScheduleRecord(
            originCode = "MAN",
            destCode = "JFK",
            departDate = LocalDate.of(ROUTE_TEST_YEAR, ROUTE_TEST_MONTH, ROUTE_TEST_DAY),
            departTime = LocalTime.of(22, 0),
            arrivalTime = LocalTime.of(8, 0),
            arrivalOffsetDays = 1,
            durationMinutes = 600,
            stops = 1,
            legDepartureTimes = listOf(LocalTime.of(22, 0), LocalTime.of(1, 30)),
            legArrivalTimes = listOf(LocalTime.of(23, 30), LocalTime.of(8, 0)),
            legArrivalOffsetDays = listOf(0, 1),
            legFlightNumbers = listOf("GA101", "GA202"),
            priceLight = BigDecimal("100.00"),
            priceEssential = BigDecimal("130.00"),
            priceFlex = BigDecimal("180.00"),
            recommendedRank = 1,
            stopoverCodes = listOf("AMS"),
            stopoverLayoverMinutes = listOf(90),
        )
}

private const val ROUTE_TEST_YEAR = 2026
private const val ROUTE_TEST_MONTH = 5
private const val ROUTE_TEST_DAY = 7
