package routes.flight

import io.ktor.http.Parameters
import testsupport.withClue
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BookPaymentHelperTest {
    @Test
    fun `decodeSeatSelection accepts base64url json and ignores invalid structures`() {
        val raw =
            base64Url(
                """
                {
                  "outbound": {
                    "0": {"1": "12A", "2": "12B"},
                    "meta": {"1": "ignored"}
                  },
                  "inbound": {
                    "1": {"1": "2C"}
                  }
                }
                """.trimIndent(),
            )

        val decoded = decodeSeatSelection(raw)

        withClue("outbound numeric leg keys are decoded") {
            assertEquals("12A", decoded.getValue("outbound").getValue("0").getValue("1"))
        }
        withClue("non-numeric leg keys are ignored") {
            assertEquals(false, decoded.getValue("outbound").containsKey("meta"))
        }
        withClue("inbound seats are decoded independently") {
            assertEquals("2C", decoded.getValue("inbound").getValue("1").getValue("1"))
        }
        withClue("bad base64 returns an empty map instead of throwing") {
            assertEquals(emptyMap(), decodeSeatSelection("not-json"))
        }
    }

    @Test
    fun `decodePaxDisplayNames keeps passenger slots and tolerates missing names`() {
        val raw =
            base64Url(
                """
                [
                  {"slot": 1, "displayName": "Ada Lovelace"},
                  {"slot": 2},
                  {"displayName": "No slot"}
                ]
                """.trimIndent(),
            )

        withClue("valid slot names are decoded") {
            assertEquals(mapOf(1 to "Ada Lovelace", 2 to ""), decodePaxDisplayNames(raw))
        }
        withClue("invalid json gives an empty passenger map") {
            assertEquals(emptyMap(), decodePaxDisplayNames("bad"))
        }
    }

    @Test
    fun `extrasSummary charges light fare seats per selected flight segment`() {
        val context =
            paymentContext(
                seatMap =
                    mapOf(
                        "outbound" to
                            mapOf(
                                "0" to mapOf("1" to "1A", "2" to ""),
                                "1" to mapOf("1" to "2A"),
                            ),
                        "inbound" to
                            mapOf(
                                "0" to mapOf("1" to "3A"),
                            ),
                    ),
                isReturn = true,
                outboundTier = "Light",
                inboundTier = "Essential",
            )

        val summary =
            extrasSummary(
                Parameters.build { append("extras", "checked-bag,unknown, priority-boarding") },
                context,
            )

        withClue("light outbound charges once for each selected segment, not once per journey") {
            assertEquals(TWO_LIGHT_SEGMENTS_FEE, summary.totalSeatFee)
        }
        withClue("non-light inbound fare has no seat fee") {
            assertEquals(listOf(TWO_LIGHT_SEGMENTS_FEE, 0), summary.feeRows.map { it["feeGbp"] })
        }
        withClue("only recognised extras are charged") {
            assertEquals(CHECKED_BAG_PLUS_PRIORITY_FEE, summary.totalBookingExtrasFee)
        }
        withClue("combined extras include seat fees and recognised booking extras") {
            assertEquals(BigDecimal("93.00"), summary.step2ExtrasAmount)
        }
    }

    @Test
    fun `paymentPassengerRows orders leg seats and falls back for missing names`() {
        val context =
            paymentContext(
                seatMap =
                    mapOf(
                        "outbound" to mapOf("1" to mapOf("1" to "9B"), "0" to mapOf("1" to "8A")),
                    ),
                paxNamesBySlot = mapOf(1 to "  "),
                paxCount = 1,
                outboundTier = "Essential",
            )

        val row = paymentPassengerRows(context).single()

        withClue("blank passenger names fall back to passenger number") {
            assertEquals("Passenger 1", row["displayName"])
        }
        withClue("seats are displayed in leg order") {
            assertEquals("8A - 9B", row["outboundSeats"])
        }
    }

    @Test
    fun `moneySummary combines passenger fares extras and per passenger total`() {
        val fare =
            FareDetails(
                dual = true,
                outboundPerPerson = BigDecimal("100.00"),
                inboundPerPerson = BigDecimal("80.00"),
                outboundPackageName = "Essential",
                inboundPackageName = "Flex",
                departingCard = null,
                returningCard = null,
                departingRouteLine = "Manchester to Paris",
                returningRouteLine = "Paris to Manchester",
            )
        val extras =
            ExtrasSummary(
                feeRows = emptyList(),
                totalSeatFee = 0,
                seatFeesAmount = BigDecimal("0.00"),
                selectedBookingExtras = emptySet(),
                bookingExtraRows = emptyList(),
                totalBookingExtrasFee = 0,
                bookingExtrasAmount = BigDecimal("0.00"),
                step2ExtrasAmount = BigDecimal("38.00"),
            )

        val summary = moneySummary(fare, extras, BigDecimal(TWO_PASSENGERS))

        withClue("flight subtotal multiplies both fares by passenger count") {
            assertEquals(BigDecimal("360.00"), summary.step1FlightsSubtotal)
        }
        withClue("grand total includes extras once") {
            assertEquals(BigDecimal("398.00"), summary.grandTotal)
        }
        withClue("per passenger total rounds to two decimal places") {
            assertEquals("199.00", summary.perPassengerAllInPlain)
        }
    }
}

private const val TWO_LIGHT_SEGMENTS_FEE = 60
private const val CHECKED_BAG_PLUS_PRIORITY_FEE = 33
private const val TWO_PASSENGERS = 2

private fun base64Url(value: String): String =
    java.util.Base64
        .getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun paymentContext(
    seatMap: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    paxNamesBySlot: Map<Int, String> = emptyMap(),
    isReturn: Boolean = false,
    paxCount: Int = TWO_PASSENGERS,
    outboundTier: String = "Light",
    inboundTier: String = "Light",
): PaymentContext =
    PaymentContext(
        seatMap = seatMap,
        paxNamesBySlot = paxNamesBySlot,
        isReturn = isReturn,
        inboundRow = null,
        outboundRow = null,
        paxCount = paxCount,
        paxMultiplier = BigDecimal(paxCount),
        cabinRaw = "economy",
        outboundTier = outboundTier,
        inboundTier = inboundTier,
    )
