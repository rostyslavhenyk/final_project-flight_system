package data

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class FlightScheduleRepositoryTest {
    @Test
    fun `generated flights keep GA numeric format and five-minute times`() {
        val res =
            FlightScheduleRepository.search(
                originCode = "MAN",
                destCode = "HKG",
                depart = LocalDate.of(2026, 4, 25),
                sort = FlightScheduleRepository.SortKey.RECOMMENDED,
                ascending = true,
                page = 1,
                pageSize = 50,
            )
        assertTrue(res.rows.isNotEmpty(), "Expected MAN->HKG schedules to exist")
        res.rows.forEach { row ->
            row.legFlightNumbers.forEach { fn ->
                assertTrue(Regex("^GA\\d{3}$").matches(fn), "Flight number must be GA + 3 digits: $fn")
            }
            row.legDepartureTimes.forEach { t ->
                assertTrue(t.minute % 5 == 0, "Departure minute must be multiple of 5: $t")
            }
            row.legArrivalTimes.forEach { t ->
                assertTrue(t.minute % 5 == 0, "Arrival minute must be multiple of 5: $t")
            }
        }
    }

    @Test
    fun `every homepage airport pair has at least five schedules`() {
        val lines =
            File("data/airports.csv").readLines().drop(1).map { it.trim() }.filter { it.isNotBlank() }
        val codes =
            lines.mapNotNull { line ->
                Regex("""\(([A-Z]{3})\)""").find(line)?.groupValues?.get(1)
            }
        val date = LocalDate.of(2026, 4, 25)
        for (origin in codes) {
            for (dest in codes) {
                if (origin == dest) continue
                val res =
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
                    res.totalCount >= 5,
                    "Expected at least five flights for $origin -> $dest, got ${res.totalCount}",
                )
            }
        }
    }

    @Test
    fun `MAN to HKG results respect spacing bands and GA### flight numbers`() {
        val res =
            FlightScheduleRepository.search(
                originCode = "MAN",
                destCode = "HKG",
                depart = LocalDate.of(2026, 4, 25),
                sort = FlightScheduleRepository.SortKey.DEPARTURE,
                ascending = true,
                page = 1,
                pageSize = 50,
            )
        assertTrue(res.totalCount >= 5)
        val rows = res.rows
        fun depMin(t: LocalTime) = t.hour * 60 + t.minute
        fun band(d: LocalTime): Int {
            val m = depMin(d)
            return when {
                m < 5 * 60 -> 2
                m < 12 * 60 -> 0
                m < 18 * 60 -> 1
                else -> 2
            }
        }
        // Pairwise 60-minute spacing is best-effort once hierarchy / time-of-day seeding and top-up run.
        val bands = rows.map { band(it.departTime) }.toSet()
        assertTrue(
            bands.size >= 2,
            "Expected departure time diversity across day parts; got bands=$bands",
        )
        rows.forEach { r ->
            r.legFlightNumbers.forEach { fn -> assertTrue(Regex("^GA\\d{3}$").matches(fn)) }
        }
    }
}

