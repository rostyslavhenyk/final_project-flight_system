package routes.flight

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightSortOption
import testsupport.withClue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightHelperTest {
    @Test
    fun `parseFlightSortOption handles known values case insensitively and falls back safely`() {
        withClue("departure maps to departure time") {
            assertEquals(FlightSortOption.DepartureTime, parseFlightSortOption("departure"))
        }
        withClue("mixed case fare maps to fare") {
            assertEquals(FlightSortOption.Fare, parseFlightSortOption("FaRe"))
        }
        withClue("unknown sort values fall back to recommended") {
            assertEquals(FlightSortOption.Recommended, parseFlightSortOption("fastest"))
        }
        withClue("missing sort values fall back to recommended") {
            assertEquals(FlightSortOption.Recommended, parseFlightSortOption(null))
        }
    }

    @Test
    fun `flightsHref encodes query values and preserves empty params correctly`() {
        withClue("empty params link to the search route without a trailing question mark") {
            assertEquals("/search-flights", flightsHref(emptyMap()))
        }
        withClue("spaces are encoded as %20 and symbols are percent encoded") {
            assertEquals(
                "/search-flights?from=Manchester%20Airport&to=Paris%20%26%20France",
                flightsHref(mapOf("from" to "Manchester Airport", "to" to "Paris & France")),
            )
        }
    }

    @Test
    fun `duration labels cover hours minutes and layovers`() {
        withClue("journey durations always include hours and minutes") {
            assertEquals("2h 5m", formatDurationMinutes(TWO_HOURS_FIVE_MINUTES))
        }
        withClue("layovers with hours and minutes use compact text") {
            assertEquals("1h 15 min", formatLayoverDuration(SEVENTY_FIVE_MINUTES))
        }
        withClue("layovers below an hour do not show zero hours") {
            assertEquals("45 min", formatLayoverDuration(FORTY_FIVE_MINUTES))
        }
    }

    @Test
    fun `pager returns no buttons for single page and a compact window for large page counts`() {
        val base = mapOf("from" to "MAN", "to" to "JFK")
        withClue("single page results do not render pager buttons") {
            assertEquals(emptyList(), buildPager(paged(page = 1, pageCount = 1), base, FlightSortOption.Fare, true))
        }

        val buttons = buildPager(paged(page = 9, pageCount = 12), base, FlightSortOption.Duration, false)
        withClue("pager keeps at most five visible buttons around the current page") {
            assertEquals(listOf(7, 8, 9, 10, 11), buttons.map { it["num"] })
        }
        withClue("current page is marked") {
            assertEquals(listOf(false, false, true, false, false), buttons.map { it["current"] })
        }
        withClue("pager links preserve requested sort order") {
            assertTrue(
                buttons
                    .first()
                    .getValue("href")
                    .toString()
                    .contains("sort=duration"),
            )
            assertTrue(
                buttons
                    .first()
                    .getValue("href")
                    .toString()
                    .contains("order=desc"),
            )
        }
    }

    @Test
    fun `carousel days disable past dates and link future dates`() {
        val today = java.time.LocalDate.now()
        val days =
            buildCarouselDays(
                selected = today,
                windowStart = today.minusDays(ONE_DAY),
                base = mapOf("from" to "MAN", "to" to "LHR"),
            )

        withClue("the first day is in the past and not clickable") {
            assertTrue(days.first().getValue("past") as Boolean)
            assertEquals("", days.first().getValue("href"))
        }
        withClue("today is selected and has a search link") {
            val todayChip = days[1]
            assertTrue(todayChip.getValue("selected") as Boolean)
            assertFalse(todayChip.getValue("href").toString().isBlank())
        }
    }

    private fun paged(
        page: Int,
        pageCount: Int,
    ): FlightSearchRepository.PagedResult =
        FlightSearchRepository.PagedResult(
            rows = emptyList(),
            totalCount = pageCount * TEN_RESULTS,
            page = page,
            pageSize = TEN_RESULTS,
            pageCount = pageCount,
        )
}

private const val ONE_DAY = 1L
private const val FORTY_FIVE_MINUTES = 45
private const val SEVENTY_FIVE_MINUTES = 75
private const val TWO_HOURS_FIVE_MINUTES = 125
private const val TEN_RESULTS = 10
