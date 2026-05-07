package routes.flight

import io.ktor.http.Parameters
import testsupport.withClue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingUrlHelperTest {
    @Test
    fun `bookingHref preserves booking state but drops unrelated and blank fields`() {
        val href =
            bookingHref(
                "/book/seats",
                Parameters.build {
                    append("from", "Manchester Airport")
                    append("to", "New York JFK")
                    append("depart", "2026-05-07")
                    append("adults", "2")
                    append("children", "")
                    append("csrf", "should-not-leak")
                    append("seatSel", "abc123")
                },
            )

        withClue("expected booking fields are preserved and encoded") {
            assertTrue(href.startsWith("/book/seats?from=Manchester%20Airport&to=New%20York%20JFK"))
            assertTrue(href.contains("adults=2"))
            assertTrue(href.contains("seatSel=abc123"))
        }
        withClue("blank and unrelated fields are not carried between booking steps") {
            assertEquals(false, href.contains("children="))
            assertEquals(false, href.contains("csrf="))
        }
    }

    @Test
    fun `bookingHref returns bare path when there is no state`() {
        withClue("empty query produces a clean path") {
            assertEquals("/book/payment", bookingHref("/book/payment", Parameters.Empty))
        }
    }

    @Test
    fun `bookPathRedirectIfCanonicalCabinNeeded redirects invalid business cabin to economy`() {
        val redirect =
            bookPathRedirectIfCanonicalCabinNeeded(
                "/book/seats",
                Parameters.build {
                    append("from", "MAN")
                    append("to", "AMS")
                    append("depart", "2026-05-07")
                    append("cabinClass", "business")
                },
            )

        withClue("regional business cabin is canonicalized to economy") {
            assertEquals("/book/seats?from=MAN&to=AMS&depart=2026-05-07&cabinClass=economy", redirect)
        }
    }

    @Test
    fun `fare tier and fare package fallbacks keep unknown values visible`() {
        withClue("blank fare defaults to flex") {
            assertEquals("flex", effectiveFareTier("   "))
        }
        withClue("known tier gets cabin-aware display name") {
            assertEquals("Business Essential", farePackageDisplayName("essential", "business"))
        }
        withClue("unknown tier is capitalized rather than hidden") {
            assertEquals("Saver", farePackageDisplayName("saver", "economy"))
        }
    }
}
