package data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import testsupport.withClue
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepositoryTest {
    @BeforeTest
    fun setUpDatabase() {
        val dbFile = Files.createTempFile("flight-system-test-", ".db").toFile()
        dbFile.deleteOnExit()
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*AllTables.all())
            SeatMaintenance.ensureUniqueSeatIndex()
        }
    }

    @Test
    fun `chat messages are stored per user and ordered by timestamp`() {
        val firstUser = testUser("Ada", "Lovelace", "ada@example.com")
        val secondUser = testUser("Grace", "Hopper", "grace@example.com")

        ChatRepository.add(firstUser.id, "Ada", "First", false)
        ChatRepository.add(secondUser.id, "Grace", "Other account", false)
        ChatRepository.add(firstUser.id, "Support Team", "Reply", true)

        val firstUserMessages = ChatRepository.getByUser(firstUser.id)

        withClue("only the selected user's conversation is returned") {
            assertEquals(listOf("First", "Reply"), firstUserMessages.map { it.message })
        }
        withClue("staff flag is preserved for replies") {
            assertEquals(listOf(false, true), firstUserMessages.map { it.isStaff })
        }
    }

    @Test
    fun `seat holds block other users but can be refreshed released and confirmed by owner`() {
        val owner = testUser("Owner", "Passenger", "owner@example.com")
        val other = testUser("Other", "Passenger", "other@example.com")

        withClue("first user can hold an available seat") {
            assertTrue(SeatRepository.hold(owner.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER))
        }
        withClue("the same user can refresh their own hold") {
            assertTrue(SeatRepository.hold(owner.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER))
        }
        withClue("another user sees the held seat as unavailable") {
            assertTrue(SeatRepository.isUnavailableForUser(FLIGHT_ID, ROW_NUMBER, SEAT_LETTER, other.id))
        }
        withClue("the holding user does not block themselves before payment") {
            assertFalse(SeatRepository.isUnavailableForUser(FLIGHT_ID, ROW_NUMBER, SEAT_LETTER, owner.id))
        }
        withClue("the owner can release their hold") {
            assertTrue(SeatRepository.release(owner.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER))
        }
        withClue("after release another user can hold the seat") {
            assertTrue(SeatRepository.hold(other.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER))
        }
    }

    @Test
    fun `confirmed seats cannot be booked again and expired holds can be claimed`() {
        val owner = testUser("Booked", "Passenger", "booked@example.com")
        val other = testUser("Late", "Passenger", "late@example.com")

        val confirmed = SeatRepository.createConfirmed(owner.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER)

        withClue("confirming an available seat creates a confirmed record") {
            assertNotNull(confirmed)
            assertEquals("CONFIRMED", confirmed.status)
        }
        withClue("a confirmed seat cannot be confirmed by another user") {
            assertEquals(null, SeatRepository.createConfirmed(other.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER))
        }

        SeatRepository.hold(owner.id, SECOND_FLIGHT_ID, ROW_NUMBER, SEAT_LETTER, holdMillis = EXPIRED_HOLD_MILLIS)
        val claimed = SeatRepository.createConfirmed(other.id, SECOND_FLIGHT_ID, ROW_NUMBER, SEAT_LETTER)

        withClue("expired reserved seats can be claimed at confirmation time") {
            assertNotNull(claimed)
            assertEquals(other.id, claimed.userId)
            assertEquals("CONFIRMED", claimed.status)
        }
    }

    @Test
    fun `purchases are scoped to the user who created them`() {
        val firstUser = testUser("Paying", "Passenger", "paying@example.com")
        val secondUser = testUser("Separate", "Passenger", "separate@example.com")

        val firstPurchase = PurchaseRepository.create(firstUser.id, amount = FIRST_AMOUNT, bookingQuery = "flight=1")
        PurchaseRepository.create(secondUser.id, amount = SECOND_AMOUNT, bookingQuery = "flight=2")

        withClue("allByUser only returns purchases for that account") {
            assertEquals(
                listOf(firstPurchase.purchaseID),
                PurchaseRepository.allByUser(firstUser.id).map { it.purchaseID },
            )
        }
        withClue("booking query is kept for account booking display") {
            assertEquals("flight=1", PurchaseRepository.get(firstPurchase.purchaseID)?.bookingQuery)
        }
    }

    @Test
    fun `booking attach to purchase marks booking paid without changing unrelated bookings`() {
        val firstUser = testUser("Booked", "Passenger", "booking-paid@example.com")
        val secondUser = testUser("Other", "Passenger", "booking-other@example.com")
        val firstSeat = SeatRepository.createConfirmed(firstUser.id, FLIGHT_ID, ROW_NUMBER, SEAT_LETTER)
        val secondSeat = SeatRepository.createConfirmed(secondUser.id, SECOND_FLIGHT_ID, ROW_NUMBER, SEAT_LETTER)
        requireNotNull(firstSeat)
        requireNotNull(secondSeat)
        val firstBooking = BookingRepository.create(FLIGHT_ID, firstUser.id, firstSeat.id)
        val secondBooking = BookingRepository.create(SECOND_FLIGHT_ID, secondUser.id, secondSeat.id)
        val purchase = PurchaseRepository.create(firstUser.id, amount = FIRST_AMOUNT)

        withClue("existing booking can be attached to a purchase") {
            assertTrue(BookingRepository.attachToPurchase(firstBooking.bookingID, purchase.purchaseID))
        }
        withClue("attached booking is marked paid and stores purchase id") {
            val updated = BookingRepository.get(firstBooking.bookingID)
            assertEquals("PAID", updated?.status)
            assertEquals(purchase.purchaseID, updated?.purchaseID)
        }
        withClue("other users' bookings are not affected") {
            val untouched = BookingRepository.get(secondBooking.bookingID)
            assertEquals("PENDING", untouched?.status)
            assertEquals(null, untouched?.purchaseID)
        }
        withClue("missing booking ids do not report success") {
            assertFalse(BookingRepository.attachToPurchase(MISSING_BOOKING_ID, purchase.purchaseID))
        }
    }

    @Test
    fun `users are normalized on create and looked up by normalized email and phone`() {
        val user =
            UserRepository.add(
                firstname = "  ada-marie  ",
                lastname = "  o'connor  ",
                roleId = CUSTOMER_ROLE_ID,
                email = "  ADA@EXAMPLE.COM ",
                password = "password",
                phone = " +44 (7700) 900123 ",
            )

        withClue("names are trimmed and capitalized while keeping separators") {
            assertEquals("Ada-Marie", user.firstname)
            assertEquals("O'Connor", user.lastname)
        }
        withClue("email is stored lowercase and can be found case-insensitively") {
            assertEquals("ada@example.com", user.email)
            assertEquals(user.id, UserRepository.getByEmail("ADA@example.com")?.id)
        }
        withClue("phone lookup ignores formatting characters") {
            assertEquals(user.id, UserRepository.getByPhone("+447700900123")?.id)
        }
    }

    private fun testUser(
        firstName: String,
        lastName: String,
        email: String,
    ): User =
        UserRepository.add(
            firstname = firstName,
            lastname = lastName,
            roleId = CUSTOMER_ROLE_ID,
            email = email,
            password = "password",
        )
}

private const val CUSTOMER_ROLE_ID = 0
private const val FLIGHT_ID = 1
private const val SECOND_FLIGHT_ID = 2
private const val ROW_NUMBER = 12
private const val SEAT_LETTER = "A"
private const val EXPIRED_HOLD_MILLIS = -1L
private const val FIRST_AMOUNT = 120.50
private const val SECOND_AMOUNT = 75.00
private const val MISSING_BOOKING_ID = 9999
