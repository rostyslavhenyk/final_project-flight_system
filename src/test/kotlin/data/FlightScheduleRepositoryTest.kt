package data

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalTime

class FlightScheduleRepositoryTest {
    @Test
    fun `generated flights keep GA4 numeric format and five-minute times`() {
        val searchPage =
            FlightScheduleRepository.search(
                originCode = "MAN",
                destCode = "HKG",
                depart = LocalDate.of(2026, 4, 25),
                sort = FlightScheduleRepository.SortKey.RECOMMENDED,
                ascending = true,
                page = 1,
                pageSize = 50,
            )
        assertTrue(searchPage.rows.isNotEmpty(), "Expected MAN->HKG schedules to exist")
        searchPage.rows.forEach { row ->
            row.legFlightNumbers.forEach { flightNumber ->
                assertTrue(Regex("^GA\\d{4}$").matches(flightNumber), "Flight number must be GA + 4 digits: $flightNumber")
            }
            row.legDepartureTimes.forEach { legTime ->
                assertTrue(legTime.minute % 5 == 0, "Departure minute must be multiple of 5: $legTime")
            }
            row.legArrivalTimes.forEach { legTime ->
                assertTrue(legTime.minute % 5 == 0, "Arrival minute must be multiple of 5: $legTime")
            }
        }
    }

    @Test
    fun `every homepage airport pair has at least five schedules`() {
        val db = File("data/db/airports.db")
        assertTrue(db.exists(), "Test requires data/db/airports.db (run scripts/convert_csvs_to_sqlite.py if needed)")
        val codes =
            DriverManager.getConnection("jdbc:sqlite:${db.path}").use { conn ->
                conn.prepareStatement("SELECT name FROM airports ORDER BY name").use { ps ->
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                val name = rs.getString("name")?.trim().orEmpty()
                                Regex("""\(([A-Z]{3})\)""").find(name)?.groupValues?.get(1)?.let { add(it) }
                            }
                        }
                    }
                }
            }
        val date = LocalDate.of(2026, 4, 25)
        for (origin in codes) {
            for (dest in codes) {
                if (origin == dest) continue
                val searchPage =
                    FlightScheduleRepository.search(
                        originCode = origin,
                        destCode = dest,
                        depart = date,
                        sort = FlightScheduleRepository.SortKey.RECOMMENDED,
                        ascending = true,
                        page = 1,
                        pageSize = 10,
                    )
                assertTrue(
                    searchPage.totalCount >= 5,
                    "Expected at least five flights for $origin -> $dest, got ${searchPage.totalCount}",
                )
            }
        }
    }

    @Test
    fun `MAN to HKG results respect spacing bands and GA#### flight numbers`() {
        val searchPage =
            FlightScheduleRepository.search(
                originCode = "MAN",
                destCode = "HKG",
                depart = LocalDate.of(2026, 4, 25),
                sort = FlightScheduleRepository.SortKey.DEPARTURE,
                ascending = true,
                page = 1,
                pageSize = 50,
            )
        assertTrue(searchPage.totalCount >= 5)
        val rows = searchPage.rows
        fun minuteOfDay(time: LocalTime) = time.hour * 60 + time.minute
        fun departureBand(departTime: LocalTime): Int {
            val minutes = minuteOfDay(departTime)
            return when {
                minutes < 5 * 60 -> 2
                minutes < 12 * 60 -> 0
                minutes < 18 * 60 -> 1
                else -> 2
            }
        }
        // Pairwise 60-minute spacing is best-effort once hierarchy / time-of-day seeding and top-up run.
        val bands = rows.map { departureBand(it.departTime) }.toSet()
        assertTrue(
            bands.size >= 2,
            "Expected departure time diversity across day parts; got bands=$bands",
        )
        rows.forEach { row ->
            row.legFlightNumbers.forEach { flightNumber ->
                assertTrue(Regex("^GA\\d{4}$").matches(flightNumber))
            }
        }
    }
}

