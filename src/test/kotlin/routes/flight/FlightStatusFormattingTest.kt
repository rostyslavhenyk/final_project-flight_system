package routes.flight

import data.Airports
import data.Countries
import data.flight.FlightSearchRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import testsupport.withClue
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlightStatusFormattingTest {
    @Test
    fun `status labels and classes default safely`() {
        withClue("delayed status gets delayed label and class") {
            assertEquals("Delayed", statusLabel("DELAYED"))
            assertEquals("status-badge--delay", statusClass("DELAYED"))
        }
        withClue("cancelled status gets cancelled label and class") {
            assertEquals("Cancelled", statusLabel("CANCELLED"))
            assertEquals("status-badge--cancelled", statusClass("CANCELLED"))
        }
        withClue("unknown status codes are displayed as on time") {
            assertEquals("On time", statusLabel("BOARDING"))
            assertEquals("status-badge--ok", statusClass("BOARDING"))
        }
    }

    @Test
    fun `time labels include day offsets only when needed`() {
        withClue("same-day scheduled times are plain HH:mm") {
            assertEquals("09:05", scheduledTimeLabel(LocalTime.of(9, 5), 0))
        }
        withClue("next-day scheduled times include offset") {
            assertEquals("23:55 +1", scheduledTimeLabel(LocalTime.of(23, 55), 1))
        }
        withClue("missing estimated times stay missing") {
            assertEquals(null, estimatedTimeLabel(null, 1))
        }
    }

    @Test
    fun `status messages adapt to route and flight number searches`() {
        val routeQuery = statusQuery(mode = "route", fromRaw = "MAN", toRaw = "AMS", anyDate = true)
        val numberQuery =
            statusQuery(
                mode = "number",
                flightNumberRaw = "GA101",
                flightDigitsOnly = "101",
                date = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY),
            )

        withClue("any-date route searches mention the upcoming window") {
            assertEquals(
                "No scheduled flights are available for this route in the next 7 days.",
                noRouteStatusMessage(routeQuery),
            )
        }
        withClue("flight-number searches include the normalized GA number and exact date") {
            assertEquals("No flights found for GA101 on 7 May 2026.", noFlightNumberStatusMessage(numberQuery))
        }
    }

    @Test
    fun `viaLine returns readable stopovers only when present`() {
        withClue("direct flights do not show a via line") {
            assertEquals(null, viaLine(statusRecord(stopovers = emptyList())))
        }
        setUpAirportLookup()
        withClue("stopovers include airport names and codes") {
            val line = viaLine(statusRecord(stopovers = listOf("AMS", "CDG"))).orEmpty()
            assertTrue(line.endsWith("(AMS) - CDG (CDG)") || line.endsWith("(AMS) - Paris Charles de Gaulle (CDG)"))
        }
    }

    private fun statusRecord(stopovers: List<String>): FlightSearchRepository.FlightStatusRecord =
        FlightSearchRepository.FlightStatusRecord(
            flightNumber = "GA101",
            originCode = "MAN",
            destCode = "JFK",
            departTime = LocalTime.of(10, 0),
            arrivalTime = LocalTime.of(14, 0),
            departureOffsetDays = 0,
            arrivalOffsetDays = 0,
            cycleDay = 1,
            available = true,
            statusCode = "SCHEDULED",
            stopoverAirportCodes = stopovers,
        )

    private fun setUpAirportLookup() {
        val dbFile = Files.createTempFile("flight-status-test-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(Countries, Airports)
            val countryId =
                Countries.insert {
                    it[name] = "Test country"
                    it[timeZone] = "UTC"
                } get Countries.id
            Airports.insert {
                it[Airports.countryId] = countryId
                it[city] = "Amsterdam"
                it[name] = "Amsterdam"
                it[code] = "AMS"
            }
            Airports.insert {
                it[Airports.countryId] = countryId
                it[city] = "Paris"
                it[name] = "Paris Charles de Gaulle"
                it[code] = "CDG"
            }
        }
    }

    private fun statusQuery(
        mode: String,
        anyDate: Boolean = false,
        flightNumberRaw: String = "",
        flightDigitsOnly: String = "",
        date: LocalDate = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY),
        fromRaw: String = "",
        toRaw: String = "",
    ): FlightStatusQuery =
        FlightStatusQuery(
            mode = mode,
            today = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY),
            date = date,
            anyDate = anyDate,
            flightNumberRaw = flightNumberRaw,
            flightDigitsOnly = flightDigitsOnly,
            flightNumber = flightDigitsOnly.takeIf { it.isNotBlank() }?.let { "GA$it" }.orEmpty(),
            fromRaw = fromRaw,
            toRaw = toRaw,
            fromCode = fromRaw.takeIf { it.isNotBlank() },
            toCode = toRaw.takeIf { it.isNotBlank() },
            page = 1,
        )
}

private const val TEST_YEAR = 2026
private const val TEST_MONTH = 5
private const val TEST_DAY = 7
