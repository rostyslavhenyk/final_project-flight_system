package routes.flight

import io.ktor.http.Parameters
import java.math.BigDecimal

internal fun fallbackPaymentTravelDates(
    queryParams: Parameters,
    isReturn: Boolean,
): TravelDates =
    TravelDates(
        departingDateDisplay =
            if (isReturn) {
                formatPayDateIso(queryParams["obDepart"])
            } else {
                formatPayDateIso(queryParams["depart"])
            },
        returningDateDisplay =
            if (isReturn) {
                formatPayDateIso(queryParams["return"]).ifBlank { formatPayDateIso(queryParams["depart"]) }
            } else {
                ""
            },
    )

internal fun fallbackPaymentOutboundPrice(
    queryParams: Parameters,
    isReturn: Boolean,
): BigDecimal =
    parseGbpAmount(
        if (isReturn) {
            queryParams["outboundPrice"].orEmpty()
        } else {
            queryParams["price"].orEmpty()
        },
    )

internal fun fallbackPaymentInboundPrice(
    queryParams: Parameters,
    isReturn: Boolean,
): BigDecimal =
    if (isReturn) {
        parseGbpAmount(queryParams["price"].orEmpty())
    } else {
        BigDecimal.ZERO
    }

internal fun fallbackPaymentOutboundTier(context: PaymentContext): String =
    if (context.isReturn) context.outboundTier else context.inboundTier

internal fun fallbackPaymentInboundPackageName(context: PaymentContext): String =
    if (context.isReturn) farePackageDisplayName(context.inboundTier, context.cabinRaw) else ""

internal fun fallbackPaymentDepartingRouteLine(
    queryParams: Parameters,
    isReturn: Boolean,
): String =
    if (isReturn) {
        routeCityPairLine(queryParams["obFrom"].orEmpty(), queryParams["obTo"].orEmpty())
    } else {
        routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
    }

internal fun fallbackPaymentReturningRouteLine(
    queryParams: Parameters,
    isReturn: Boolean,
): String =
    if (isReturn) {
        routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
    } else {
        ""
    }
