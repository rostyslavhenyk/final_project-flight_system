package data.flight

import data.AllTables
import data.BookingRepository
import data.FlightRepository
import data.SeatMaintenance
import data.SeatRepository
import data.UserRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import testsupport.withClue
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FlightScheduleGeneratorTest {
    @BeforeTest
    fun setUpDatabase() {
        val dbFile = Files.createTempFile("flight-schedule-generator-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(*AllTables.all())
            SeatMaintenance.ensureUniqueSeatIndex()
        }
    }

    @Test
    fun `ensureSeedData creates stable airports routes templates and future flights`() {
        transaction { FlightScheduleGenerator.ensureSeedData() }
        val firstFlightCount = FlightRepository.all().size
        val firstTemplateCount = transaction { FlightScheduleTemplateRepository.all().size }

        transaction { FlightScheduleGenerator.ensureSeedData() }

        withClue("seed data creates flight schedule templates") {
            assertTrue(firstTemplateCount > 0)
        }
        withClue("seed data creates flights") {
            assertTrue(firstFlightCount > 0)
        }
        withClue("running seed twice does not duplicate generated flights") {
            assertEquals(firstFlightCount, FlightRepository.all().size)
        }
        withClue("generated flights stay within the rolling future window") {
            val todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN).toString()
            assertTrue(FlightRepository.all().all { flight -> flight.departureTime >= todayStart })
        }
    }

    @Test
    fun `ensureSeedData removes expired unbooked flights but preserves booked past flights`() {
        transaction { FlightScheduleGenerator.ensureSeedData() }
        val routeId = FlightRepository.all().first().routeID
        val user =
            UserRepository.add(
                firstname = "Past",
                lastname = "Passenger",
                roleId = CUSTOMER_ROLE_ID,
                email = "past@abc.com",
                password = "password",
            )
        val expiredUnbooked =
            FlightRepository.add(
                routeID = routeId,
                departureTime =
                    LocalDate
                        .now()
                        .minusDays(2)
                        .atTime(9, 0)
                        .toString(),
                arrivalTime =
                    LocalDate
                        .now()
                        .minusDays(2)
                        .atTime(10, 0)
                        .toString(),
                price = 50.0,
                status = "scheduled",
            )
        val expiredBooked =
            FlightRepository.add(
                routeID = routeId,
                departureTime =
                    LocalDate
                        .now()
                        .minusDays(1)
                        .atTime(9, 0)
                        .toString(),
                arrivalTime =
                    LocalDate
                        .now()
                        .minusDays(1)
                        .atTime(10, 0)
                        .toString(),
                price = 50.0,
                status = "scheduled",
            )
        val seat = SeatRepository.createConfirmed(user.id, expiredBooked.flightID, 1, "A")
        assertNotNull(seat)
        BookingRepository.create(expiredBooked.flightID, user.id, seat.id, status = "PAID")

        transaction { FlightScheduleGenerator.ensureSeedData() }

        withClue("past flights without bookings are deleted") {
            assertEquals(null, FlightRepository.get(expiredUnbooked.flightID))
        }
        withClue("past flights with bookings are preserved for booking history") {
            assertNotNull(FlightRepository.get(expiredBooked.flightID))
        }
    }
}

private const val CUSTOMER_ROLE_ID = 0
