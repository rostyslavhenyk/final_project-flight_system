package routes.flight

import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode

internal fun loyaltyPointsForBooking(queryParams: Parameters): Int {
    val context = paymentContext(queryParams)
    val fare = fareDetails(queryParams, context)
    val outboundPoints =
        loyaltyPointsForFare(
            tier = context.outboundTier,
            perPassengerFare = fare.outboundPerPerson,
            passengerCount = context.paxCount,
        )
    val hasInboundFare = fare.inboundPerPerson.compareTo(BigDecimal.ZERO) > 0
    val inboundPoints =
        if (fare.dual || (context.isReturn && hasInboundFare)) {
            loyaltyPointsForFare(
                tier = context.inboundTier,
                perPassengerFare = fare.inboundPerPerson,
                passengerCount = context.paxCount,
            )
        } else {
            0
        }
    return outboundPoints + inboundPoints
}

private fun loyaltyPointsForFare(
    tier: String,
    perPassengerFare: BigDecimal,
    passengerCount: Int,
): Int {
    val fareTotal = perPassengerFare.multiply(BigDecimal.valueOf(passengerCount.toLong()))
    val earned = fareTotal.multiply(earningRateForTier(tier))
    return earned.setScale(0, RoundingMode.HALF_UP).toInt().coerceAtLeast(0)
}

private fun earningRateForTier(tier: String): BigDecimal =
    when (tier.lowercase()) {
        "light" -> BigDecimal("0.25")
        "essential" -> BigDecimal("0.60")
        "flex" -> BigDecimal("0.90")
        else -> BigDecimal.ZERO
    }
