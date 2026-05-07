package routes.flight

import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode

internal fun fareDetails(
    queryParams: Parameters,
    context: PaymentContext,
): FareDetails =
    when {
        context.isDualLeg() -> dualFareDetails(queryParams, context)
        context.inboundRow != null -> singleRecordFareDetails(queryParams, context)
        else -> fallbackFareDetails(queryParams, context)
    }

internal fun moneySummary(
    fare: FareDetails,
    extras: ExtrasSummary,
    paxMultiplier: BigDecimal,
): MoneySummary {
    val outboundLineTotal = fare.outboundPerPerson.timesPax(paxMultiplier)
    val inboundLineTotal = fare.inboundPerPerson.timesPax(paxMultiplier)
    val step1FlightsSubtotal = outboundLineTotal.add(inboundLineTotal).moneyScale()
    val step1CombinedPerPerson = fare.outboundPerPerson.add(fare.inboundPerPerson).moneyScale()
    val grandTotal = step1FlightsSubtotal.add(extras.step2ExtrasAmount).moneyScale()
    return MoneySummary(
        outboundLineTotal = outboundLineTotal,
        inboundLineTotal = inboundLineTotal,
        step1FlightsSubtotal = step1FlightsSubtotal,
        step1CombinedPerPerson = step1CombinedPerPerson,
        grandTotal = grandTotal,
        perPassengerAllInPlain = formatGbpPlain(grandTotal.divide(paxMultiplier, 2, RoundingMode.HALF_UP)),
    )
}

internal fun step1BreakdownRows(
    fare: FareDetails,
    isReturn: Boolean,
): List<Map<String, String>> =
    buildList {
        val hasReturnPrice = fare.inboundPerPerson.compareTo(BigDecimal.ZERO) != 0
        when {
            fare.dual || (isReturn && hasReturnPrice) -> addReturnBreakdownRows(fare)
            fare.outboundPackageName.isNotBlank() ->
                add(fareBreakdownRow("Fare", fare.outboundPackageName, fare.outboundPerPerson))
            else -> add(simpleBreakdownRow("Flight fare (per person)", fare.outboundPerPerson))
        }
    }

internal fun travelDates(
    queryParams: Parameters,
    context: PaymentContext,
    dual: Boolean,
): TravelDates =
    when {
        dual ->
            TravelDates(
                departingDateDisplay = formatPayDateRecord(context.outboundRow!!.departDate),
                returningDateDisplay = formatPayDateRecord(context.inboundRow!!.departDate),
            )
        context.inboundRow != null ->
            TravelDates(
                departingDateDisplay =
                    formatPayDateRecord(context.inboundRow.departDate)
                        .ifBlank { formatPayDateIso(queryParams["depart"]) },
                returningDateDisplay = "",
            )
        else -> fallbackPaymentTravelDates(queryParams, context.isReturn)
    }

private fun dualFareDetails(
    queryParams: Parameters,
    context: PaymentContext,
): FareDetails {
    val outboundRow = context.outboundRow!!
    val inboundRow = context.inboundRow!!
    return FareDetails(
        dual = true,
        outboundPerPerson = moneyForTier(cabinFareSet(outboundRow, context.cabinRaw), context.outboundTier),
        inboundPerPerson = moneyForTier(cabinFareSet(inboundRow, context.cabinRaw), context.inboundTier),
        outboundPackageName = farePackageDisplayName(context.outboundTier, context.cabinRaw),
        inboundPackageName = farePackageDisplayName(context.inboundTier, context.cabinRaw),
        departingCard = flightCardMap(outboundRow, context.cabinRaw),
        returningCard = flightCardMap(inboundRow, context.cabinRaw),
        departingRouteLine = routeCityPairLine(queryParams["obFrom"].orEmpty(), queryParams["obTo"].orEmpty()),
        returningRouteLine = routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty()),
    )
}

private fun singleRecordFareDetails(
    queryParams: Parameters,
    context: PaymentContext,
): FareDetails {
    val inboundRow = context.inboundRow!!
    return FareDetails(
        dual = false,
        outboundPerPerson = moneyForTier(cabinFareSet(inboundRow, context.cabinRaw), context.inboundTier),
        inboundPerPerson = BigDecimal.ZERO,
        outboundPackageName = farePackageDisplayName(context.inboundTier, context.cabinRaw),
        inboundPackageName = "",
        departingCard = flightCardMap(inboundRow, context.cabinRaw),
        returningCard = null,
        departingRouteLine = routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty()),
        returningRouteLine = "",
    )
}

private fun fallbackFareDetails(
    queryParams: Parameters,
    context: PaymentContext,
): FareDetails =
    FareDetails(
        dual = false,
        outboundPerPerson = fallbackPaymentOutboundPrice(queryParams, context.isReturn),
        inboundPerPerson = fallbackPaymentInboundPrice(queryParams, context.isReturn),
        outboundPackageName = farePackageDisplayName(fallbackPaymentOutboundTier(context), context.cabinRaw),
        inboundPackageName = fallbackPaymentInboundPackageName(context),
        departingCard = null,
        returningCard = null,
        departingRouteLine = fallbackPaymentDepartingRouteLine(queryParams, context.isReturn),
        returningRouteLine = fallbackPaymentReturningRouteLine(queryParams, context.isReturn),
    )

private fun MutableList<Map<String, String>>.addReturnBreakdownRows(fare: FareDetails) {
    add(fareBreakdownRow("Outbound", fare.outboundPackageName, fare.outboundPerPerson))
    add(fareBreakdownRow("Return", fare.inboundPackageName, fare.inboundPerPerson))
}

private fun simpleBreakdownRow(
    label: String,
    amount: BigDecimal,
): Map<String, String> = mapOf("label" to label, "amountPlain" to formatGbpPlain(amount))

private fun fareBreakdownRow(
    label: String,
    packageName: String,
    amount: BigDecimal,
): Map<String, String> = simpleBreakdownRow("$label - $packageName", amount)

private fun PaymentContext.isDualLeg(): Boolean = isReturn && outboundRow != null && inboundRow != null

private fun BigDecimal.timesPax(paxMultiplier: BigDecimal): BigDecimal = multiply(paxMultiplier).moneyScale()

private fun BigDecimal.moneyScale(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
