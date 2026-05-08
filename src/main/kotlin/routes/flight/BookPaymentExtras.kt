package routes.flight

import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val LIGHT_JOURNEY_SEAT_FEE_GBP = 30
private const val CHECKED_BAG_PRICE_GBP = 25
private const val PRIORITY_BOARDING_PRICE_GBP = 8
private const val TRAVEL_INSURANCE_PRICE_GBP = 14

private data class BookingExtra(
    val id: String,
    val label: String,
    val priceGbp: Int,
)

private val bookingExtras =
    listOf(
        BookingExtra("checked-bag", "Checked bag", CHECKED_BAG_PRICE_GBP),
        BookingExtra("priority-boarding", "Priority boarding", PRIORITY_BOARDING_PRICE_GBP),
        BookingExtra("travel-insurance", "Travel insurance", TRAVEL_INSURANCE_PRICE_GBP),
    )

internal fun extrasSummary(
    queryParams: Parameters,
    context: PaymentContext,
): ExtrasSummary {
    val feeRows = seatFeeRows(context)
    val totalSeatFee = feeRows.sumOf { row -> row["feeGbp"] as Int }
    val selectedBookingExtras = selectedBookingExtras(queryParams)
    val bookingExtraRows = bookingExtraRows(selectedBookingExtras)
    val totalBookingExtrasFee = bookingExtraRows.sumOf { row -> row["feeGbp"] as Int }
    val seatFeesAmount = gbpAmount(totalSeatFee)
    val bookingExtrasAmount = gbpAmount(totalBookingExtrasFee)
    return ExtrasSummary(
        feeRows = feeRows,
        totalSeatFee = totalSeatFee,
        seatFeesAmount = seatFeesAmount,
        selectedBookingExtras = selectedBookingExtras,
        bookingExtraRows = bookingExtraRows,
        totalBookingExtrasFee = totalBookingExtrasFee,
        bookingExtrasAmount = bookingExtrasAmount,
        step2ExtrasAmount = seatFeesAmount.add(bookingExtrasAmount).setScale(2, RoundingMode.HALF_UP),
    )
}

internal fun paymentPassengerRows(context: PaymentContext): List<Map<String, Any?>> =
    List(context.paxCount) { index ->
        val slot = index + 1
        mapOf(
            "slot" to slot,
            "displayName" to passengerName(context.paxNamesBySlot, slot),
            "outboundSeats" to seatSlotsLine(context.seatMap["outbound"], slot, context.outboundTier.isLightFare()),
            "returnSeats" to returnSeatSlotsLine(context, slot),
        )
    }

private fun seatFeeRows(context: PaymentContext): List<Map<String, Any?>> =
    buildList {
        add(
            seatFeeRow(
                journeyLabel = if (context.isReturn) "Outbound journey" else "Selected journey",
                fareTierRaw = context.outboundTier,
                selectedLegCount = selectedLegCountForJourney(context.seatMap, "outbound"),
            ),
        )
        if (context.isReturn) {
            add(
                seatFeeRow(
                    journeyLabel = "Return journey",
                    fareTierRaw = context.inboundTier,
                    selectedLegCount = selectedLegCountForJourney(context.seatMap, "inbound"),
                ),
            )
        }
    }

private fun selectedBookingExtras(queryParams: Parameters): Set<String> =
    queryParams["extras"]
        ?.split(",")
        ?.map { extraId -> extraId.trim() }
        ?.filter { extraId -> extraId.isNotBlank() }
        ?.toSet()
        .orEmpty()

private fun bookingExtraRows(selectedBookingExtras: Set<String>): List<Map<String, Any?>> =
    bookingExtras
        .filter { extra -> extra.id in selectedBookingExtras }
        .map { extra ->
            mapOf(
                "id" to extra.id,
                "label" to extra.label,
                "feeGbp" to extra.priceGbp,
                "amountPlain" to formatGbpPlain(gbpAmount(extra.priceGbp)),
            )
        }

private fun seatFeeRow(
    journeyLabel: String,
    fareTierRaw: String,
    selectedLegCount: Int,
): Map<String, Any?> {
    val fee =
        if (fareTierRaw.isLightFare() && selectedLegCount > 0) {
            LIGHT_JOURNEY_SEAT_FEE_GBP * selectedLegCount
        } else {
            0
        }
    val label =
        if (selectedLegCount > 1) {
            "$journeyLabel ($selectedLegCount flight segments)"
        } else {
            journeyLabel
        }
    return mapOf("journeyLabel" to label, "feeGbp" to fee)
}

private fun returnSeatSlotsLine(
    context: PaymentContext,
    slot: Int,
): String =
    if (context.isReturn) {
        seatSlotsLine(context.seatMap["inbound"], slot, context.inboundTier.isLightFare())
    } else {
        ""
    }

private fun seatSlotsLine(
    legs: Map<String, Map<String, String>>?,
    slot: Int,
    collapseLightAllUnselected: Boolean,
): String {
    val parts = selectedSeatParts(legs, slot)
    return when {
        parts.isEmpty() -> "Not selected"
        collapseLightAllUnselected && parts.all { part -> part == "Not selected" } -> "Not selected"
        else -> parts.joinToString(" - ")
    }
}

private fun selectedSeatParts(
    legs: Map<String, Map<String, String>>?,
    slot: Int,
): List<String> {
    val slotKey = slot.toString()
    return legs
        ?.keys
        ?.mapNotNull { legIndex -> legIndex.toIntOrNull() }
        ?.sorted()
        ?.map { legIndex ->
            legs[legIndex.toString()]?.get(slotKey)?.takeIf { seat -> seat.isNotBlank() } ?: "Not selected"
        }.orEmpty()
}

private fun passengerName(
    names: Map<Int, String>,
    slot: Int,
): String = names[slot]?.trim().orEmpty().ifBlank { "Passenger $slot" }

private fun selectedLegCountForJourney(
    seatMap: Map<String, Map<String, Map<String, String>>>,
    journeyKey: String,
): Int =
    seatMap[journeyKey]
        ?.values
        ?.count { passengerSeats -> passengerSeats.values.any { seat -> seat.isNotBlank() } }
        ?: 0

private fun String.isLightFare(): Boolean = lowercase(Locale.UK).trim() == "light"

private fun gbpAmount(amount: Int): BigDecimal = BigDecimal.valueOf(amount.toLong()).setScale(2, RoundingMode.HALF_UP)
