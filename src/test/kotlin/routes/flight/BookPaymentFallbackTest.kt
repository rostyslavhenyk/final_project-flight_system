package routes.flight

import io.ktor.http.Parameters
import testsupport.withClue
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BookPaymentFallbackTest {
    @Test
    fun `parseGbpAmount accepts formatted pounds and fails closed to zero`() {
        withClue("plain numbers are scaled to two decimals") {
            assertEquals(BigDecimal("123.40"), parseGbpAmount("123.4"))
        }
        withClue("comma separated values are accepted") {
            assertEquals(BigDecimal("1234.50"), parseGbpAmount("1,234.5"))
        }
        withClue("invalid money values are treated as zero") {
            assertEquals(BigDecimal.ZERO, parseGbpAmount("free"))
        }
        withClue("missing money values are treated as zero") {
            assertEquals(BigDecimal.ZERO, parseGbpAmount(null))
        }
    }

    @Test
    fun `formatPayDateIso handles valid blank and invalid values`() {
        withClue("valid iso dates display in UK long format") {
            assertEquals("7 May 2026", formatPayDateIso("2026-05-07"))
        }
        withClue("blank dates return blank rather than today's date") {
            assertEquals("", formatPayDateIso("   "))
        }
        withClue("invalid dates return blank rather than throwing") {
            assertEquals("", formatPayDateIso("07/05/2026"))
        }
    }

    @Test
    fun `fallback travel dates use outbound fields for return bookings`() {
        val params =
            Parameters.build {
                append("obDepart", "2026-05-07")
                append("depart", "2026-05-14")
                append("return", "")
            }

        val dates = fallbackPaymentTravelDates(params, isReturn = true)

        withClue("departing date comes from outbound departure for return bookings") {
            assertEquals("7 May 2026", dates.departingDateDisplay)
        }
        withClue("blank return date falls back to the current inbound depart date") {
            assertEquals("14 May 2026", dates.returningDateDisplay)
        }
    }

    @Test
    fun `fallback prices choose outbound and inbound fields based on trip type`() {
        val returnParams =
            Parameters.build {
                append("outboundPrice", "99.99")
                append("price", "88.50")
            }
        val oneWayParams = Parameters.build { append("price", "77.25") }

        withClue("return outbound price comes from outboundPrice") {
            assertEquals(BigDecimal("99.99"), fallbackPaymentOutboundPrice(returnParams, isReturn = true))
        }
        withClue("return inbound price comes from selected inbound price") {
            assertEquals(BigDecimal("88.50"), fallbackPaymentInboundPrice(returnParams, isReturn = true))
        }
        withClue("one-way outbound price comes from price") {
            assertEquals(BigDecimal("77.25"), fallbackPaymentOutboundPrice(oneWayParams, isReturn = false))
        }
        withClue("one-way inbound price is zero") {
            assertEquals(BigDecimal.ZERO, fallbackPaymentInboundPrice(oneWayParams, isReturn = false))
        }
    }
}
