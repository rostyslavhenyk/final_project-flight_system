package routes.flight

import data.flight.FlightSearchRepository.FlightScheduleRecord
import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_ADULTS = 9
private const val MAX_CHILDREN = 8

private val payTravelDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

internal fun bookPaymentModel(queryParams: Parameters): Map<String, Any?> {
    val context = paymentContext(queryParams)
    val fare = fareDetails(queryParams, context)
    val extras = extrasSummary(queryParams, context)
    val money = moneySummary(fare, extras, context.paxMultiplier)
    val dates = travelDates(queryParams, context, fare.dual)
    return paymentModel(queryParams, context, fare, extras, money, dates)
}

private fun paymentContext(queryParams: Parameters): PaymentContext {
    val isReturn = queryParams["trip"].equals("return", ignoreCase = true)
    val inboundRow = findRecordForBooking(queryParams)
    val outboundRow = findOutboundRecordForBooking(queryParams)
    val paxCount = passengerCount(queryParams)
    val cabinRaw =
        CabinNormalization.normalizedCabinForLegs(
            queryParams["cabinClass"].orEmpty(),
            cabinLegs(isReturn, outboundRow, inboundRow),
        )
    return PaymentContext(
        seatMap = decodeSeatSelection(queryParams["seatSel"]),
        paxNamesBySlot = decodePaxDisplayNames(queryParams["paxSel"]),
        isReturn = isReturn,
        inboundRow = inboundRow,
        outboundRow = outboundRow,
        paxCount = paxCount,
        paxMultiplier = BigDecimal.valueOf(paxCount.toLong()),
        cabinRaw = cabinRaw,
        outboundTier = outboundTier(queryParams, isReturn),
        inboundTier = effectiveFareTier(queryParams["fare"].orEmpty()),
    )
}

private fun paymentModel(
    queryParams: Parameters,
    context: PaymentContext,
    fare: FareDetails,
    extras: ExtrasSummary,
    money: MoneySummary,
    dates: TravelDates,
): Map<String, Any?> =
    navigationModel(queryParams) +
        extrasModel(context, extras) +
        moneyModel(context, fare, extras, money) +
        fareModel(context, fare, dates) +
        mapOf(
            "title" to "Confirm and pay",
            "paxCount" to context.paxCount,
            "passengerRows" to paymentPassengerRows(context),
        )

private fun navigationModel(queryParams: Parameters): Map<String, Any?> =
    mapOf(
        "chooseFlightsHref" to backToFlightSearchHref(queryParams),
        "passengersHref" to bookingHref("/book/passengers", queryParams),
        "seatsHref" to bookingHref("/book/seats", queryParams),
    )

private fun extrasModel(
    context: PaymentContext,
    extras: ExtrasSummary,
): Map<String, Any?> =
    mapOf(
        "feeRows" to extras.feeRows,
        "totalSeatFeeGbp" to extras.totalSeatFee,
        "seatFeesSubtotalPlain" to formatGbpPlain(extras.seatFeesAmount),
        "bookingExtraRows" to extras.bookingExtraRows,
        "totalBookingExtrasFeeGbp" to extras.totalBookingExtrasFee,
        "bookingExtrasSubtotalPlain" to formatGbpPlain(extras.bookingExtrasAmount),
        "totalStep2ExtrasGbp" to (extras.totalSeatFee + extras.totalBookingExtrasFee),
        "hasSeatSelection" to context.seatMap.isNotEmpty(),
        "hasBookingExtras" to extras.selectedBookingExtras.isNotEmpty(),
        "step2ExtrasSubtotalPlain" to formatGbpPlain(extras.step2ExtrasAmount),
    )

private fun moneyModel(
    context: PaymentContext,
    fare: FareDetails,
    extras: ExtrasSummary,
    money: MoneySummary,
): Map<String, Any?> =
    mapOf(
        "grandTotalPlain" to formatGbpPlain(money.grandTotal),
        "perPassengerCombinedPlain" to money.perPassengerAllInPlain,
        "step1SubtotalPlain" to formatGbpPlain(money.step1FlightsSubtotal),
        "step1FlightsPerPersonSubtotalPlain" to formatGbpPlain(money.step1CombinedPerPerson),
        "step1BreakdownRows" to step1BreakdownRows(fare, context.isReturn),
        "outboundPerPersonPlain" to formatGbpPlain(fare.outboundPerPerson),
        "inboundPerPersonPlain" to formatGbpPlain(fare.inboundPerPerson),
        "outboundLineTotalPlain" to formatGbpPlain(money.outboundLineTotal),
        "inboundLineTotalPlain" to formatGbpPlain(money.inboundLineTotal),
        "step2ExtrasAmount" to extras.step2ExtrasAmount,
    )

private fun fareModel(
    context: PaymentContext,
    fare: FareDetails,
    dates: TravelDates,
): Map<String, Any?> =
    mapOf(
        "dualLeg" to fare.dual,
        "isReturn" to context.isReturn,
        "departingCard" to fare.departingCard,
        "returningCard" to fare.returningCard,
        "departingRouteLine" to fare.departingRouteLine,
        "returningRouteLine" to fare.returningRouteLine,
        "departingDateDisplay" to dates.departingDateDisplay,
        "returningDateDisplay" to dates.returningDateDisplay,
        "outboundPackageName" to fare.outboundPackageName,
        "inboundPackageName" to fare.inboundPackageName,
        "hasDepartingCard" to (fare.departingCard != null),
        "hasReturningCard" to (fare.returningCard != null),
    )

private fun passengerCount(queryParams: Parameters): Int {
    val adults = queryParams["adults"]?.toIntOrNull()?.coerceIn(1, MAX_ADULTS) ?: 1
    val children = queryParams["children"]?.toIntOrNull()?.coerceIn(0, MAX_CHILDREN) ?: 0
    return adults + children
}

private fun outboundTier(
    queryParams: Parameters,
    isReturn: Boolean,
): String = effectiveFareTier(if (isReturn) queryParams["obFare"].orEmpty() else queryParams["fare"].orEmpty())

private fun cabinLegs(
    isReturn: Boolean,
    outboundRow: FlightScheduleRecord?,
    inboundRow: FlightScheduleRecord?,
): List<Pair<String, String>> =
    buildList {
        if (isReturn && outboundRow != null) add(outboundRow.originCode to outboundRow.destCode)
        if (inboundRow != null) add(inboundRow.originCode to inboundRow.destCode)
    }

internal fun formatPayDateRecord(date: LocalDate): String = date.format(payTravelDateFormat)

internal fun formatPayDateIso(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    return if (trimmed.isBlank()) {
        ""
    } else {
        runCatching { LocalDate.parse(trimmed).format(payTravelDateFormat) }.getOrElse { "" }
    }
}

internal fun parseGbpAmount(raw: String?): BigDecimal {
    val normalized =
        raw
            ?.trim()
            ?.replace(",", "")
            ?.replace("£", "")
            .orEmpty()
    return normalized.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
}

internal fun formatGbpPlain(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
