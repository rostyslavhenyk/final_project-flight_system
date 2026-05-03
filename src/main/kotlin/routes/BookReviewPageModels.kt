package routes

import data.flight.FlightSearchRepository.FlightScheduleRecord
import io.ktor.http.Parameters
import java.math.BigDecimal
import java.util.Locale

internal fun missingReviewModel(queryParams: Parameters): Map<String, Any?> =
    mapOf(
        "title" to "Selection not found",
        "backHref" to backToFlightSearchHref(queryParams),
    )

internal fun bookReviewModel(
    queryParams: Parameters,
    inboundRow: FlightScheduleRecord,
): Map<String, Any?> {
    val cabinRaw = queryParams["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
    val outboundRow = findOutboundRecordForBooking(queryParams)
    val isDualLegReview = queryParams["trip"].equals("return", ignoreCase = true) && outboundRow != null
    val fareSummary = reviewFareSummary(queryParams, inboundRow, outboundRow, cabinRaw, isDualLegReview)
    val selectAnotherDepartingFlightHref = selectAnotherDepartingFlightHref(queryParams, isDualLegReview)
    val selectAnotherReturningFlightHref = if (isDualLegReview) inboundSearchResultsHref(queryParams) else ""
    return mapOf(
        "title" to "Review your flights",
        "isDualLegReview" to isDualLegReview,
        "departingCard" to flightCardMap(outboundRow.takeIf { isDualLegReview } ?: inboundRow, cabinRaw),
        "returningCard" to if (isDualLegReview) flightCardMap(inboundRow, cabinRaw) else null,
        "isEconomyCabin" to (cabinRaw != "business" && cabinRaw != "first"),
        "isBusinessCabin" to (cabinRaw == "business"),
        "isFirstCabin" to (cabinRaw == "first"),
        "departingTier" to fareSummary.departingTier,
        "returningTier" to if (isDualLegReview) fareSummary.inboundTier else "",
        "departingPackageName" to fareSummary.departingPackageName,
        "returningPackageName" to if (isDualLegReview) fareSummary.inboundPackageName else "",
        "departingPrice" to formatMoney(fareSummary.departingPrice),
        "returningPrice" to if (isDualLegReview) formatMoney(fareSummary.inboundPrice) else "",
        "departingRouteLine" to departingRouteLine(queryParams, isDualLegReview),
        "returningRouteLine" to returningRouteLine(queryParams, isDualLegReview),
        "continuePassengersHref" to bookingHref("/book/passengers", queryParams),
        "selectAnotherDepartingFlightHref" to selectAnotherDepartingFlightHref,
        "selectAnotherDepartingFareHref" to selectAnotherDepartingFlightHref,
        "selectAnotherReturningFlightHref" to selectAnotherReturningFlightHref,
        "selectAnotherReturningFareHref" to selectAnotherReturningFlightHref,
        "highlightSelection" to (queryParams["highlight"] == "1"),
    )
}

private data class ReviewFareSummary(
    val inboundTier: String,
    val inboundPackageName: String,
    val inboundPrice: BigDecimal,
    val departingTier: String,
    val departingPackageName: String,
    val departingPrice: BigDecimal,
)

private fun reviewFareSummary(
    queryParams: Parameters,
    inboundRow: FlightScheduleRecord,
    outboundRow: FlightScheduleRecord?,
    cabinRaw: String,
    isDualLegReview: Boolean,
): ReviewFareSummary {
    val inboundFares = cabinFareSet(inboundRow, cabinRaw)
    val inboundTier = effectiveFareTier(queryParams["fare"].orEmpty().lowercase(Locale.UK).trim(), cabinRaw)
    val outboundTier = effectiveFareTier(queryParams["obFare"].orEmpty().lowercase(Locale.UK).trim(), cabinRaw)
    val inboundPrice = moneyForTier(inboundFares, inboundTier)
    val outboundPrice = moneyForTier(outboundRow?.let { cabinFareSet(it, cabinRaw) } ?: inboundFares, outboundTier)
    return ReviewFareSummary(
        inboundTier = inboundTier,
        inboundPackageName = farePackageDisplayName(inboundTier, cabinRaw),
        inboundPrice = inboundPrice,
        departingTier = if (isDualLegReview) outboundTier else inboundTier,
        departingPackageName = farePackageDisplayName(if (isDualLegReview) outboundTier else inboundTier, cabinRaw),
        departingPrice = if (isDualLegReview) outboundPrice else inboundPrice,
    )
}

private fun departingRouteLine(
    queryParams: Parameters,
    isDualLegReview: Boolean,
): String =
    if (isDualLegReview) {
        routeCityPairLine(queryParams["obFrom"].orEmpty(), queryParams["obTo"].orEmpty())
    } else {
        routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
    }

private fun returningRouteLine(
    queryParams: Parameters,
    isDualLegReview: Boolean,
): String =
    if (isDualLegReview) {
        routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
    } else {
        ""
    }

private fun selectAnotherDepartingFlightHref(
    queryParams: Parameters,
    isDualLegReview: Boolean,
): String =
    if (isDualLegReview) {
        flightsHref(buildOutboundLegSearchParams(queryParams))
    } else {
        flightsHref(
            buildBaseParams(
                queryParams,
                queryParams["from"].orEmpty(),
                queryParams["to"].orEmpty(),
                queryParams["depart"].orEmpty(),
            ) + mapOf("page" to "1"),
        )
    }
