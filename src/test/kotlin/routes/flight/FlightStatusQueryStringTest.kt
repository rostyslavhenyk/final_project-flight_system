package routes.flight

import data.AllTables
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import testsupport.withClue
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightStatusQueryStringTest {
    @BeforeTest
    fun setUpDatabase() {
        val dbFile = Files.createTempFile("flight-status-query-test-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*AllTables.all())
        }
    }

    @Test
    fun `flightStatusQueryString encodes route queries and clamps page`() {
        val query =
            flightStatusQueryString(
                mode = "route",
                date = LocalDate.of(2026, 5, 7),
                anyDate = true,
                flightDigits = "",
                fromRaw = "Manchester Airport",
                toRaw = "Paris & France",
                page = -2,
            )

        withClue("route query includes encoded airports and any-date flag") {
            assertEquals(
                "mode=route&date=2026-05-07&anyDate=1&from=Manchester+Airport&to=Paris+%26+France&page=1",
                query,
            )
        }
    }

    @Test
    fun `flightStatusQueryString includes flight number only for number mode with digits`() {
        val query =
            flightStatusQueryString(
                mode = "flight-number",
                date = LocalDate.of(2026, 5, 7),
                anyDate = false,
                flightDigits = "0123",
                fromRaw = "MAN",
                toRaw = "AMS",
                page = 3,
            )

        withClue("flight number mode carries only digits and page state") {
            assertEquals("mode=flight-number&date=2026-05-07&flightNumber=0123&page=3", query)
        }
        withClue("route fields are not mixed into flight-number pagination links") {
            assertFalse(query.contains("from="))
            assertFalse(query.contains("to="))
        }
    }

    @Test
    fun `flightStatusPageModel validates incomplete flight number queries without database search`() {
        val model = flightStatusPageModel(parametersWithFlightNumber("GA"))

        withClue("letters without digits are treated as an invalid flight-number query") {
            assertEquals("Enter the flight number digits after GA (for example: 1285).", model["formError"])
            assertEquals(true, model["hasQuery"])
        }
        withClue("flight number is blank when no digits are present") {
            assertEquals("", model["flightNumber"])
        }
    }

    @Test
    fun `flightStatusPageModel strips extra flight digits to the supported limit`() {
        val model = flightStatusPageModel(parametersWithFlightNumber("GA123456"))

        withClue("only the first four digits are used") {
            assertEquals("1234", model["flightDigits"])
            assertEquals("GA1234", model["flightNumber"])
        }
        withClue("a syntactically valid but unknown flight returns a no-results message") {
            assertTrue(model["formError"].toString().contains("No flights found for GA1234"))
        }
    }
}

private fun parametersWithFlightNumber(value: String): io.ktor.http.Parameters =
    io.ktor.http.Parameters.build {
        append("mode", "flight-number")
        append("flightNumber", value)
    }
