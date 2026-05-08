package routes.flight

import data.flight.FlightSearchRepository.FlightScheduleRecord
import java.math.BigDecimal

internal data class PaymentContext(
    val seatMap: Map<String, Map<String, Map<String, String>>>,
    val paxNamesBySlot: Map<Int, String>,
    val isReturn: Boolean,
    val inboundRow: FlightScheduleRecord?,
    val outboundRow: FlightScheduleRecord?,
    val paxCount: Int,
    val paxMultiplier: BigDecimal,
    val cabinRaw: String,
    val outboundTier: String,
    val inboundTier: String,
)

internal data class FareDetails(
    val dual: Boolean,
    val outboundPerPerson: BigDecimal,
    val inboundPerPerson: BigDecimal,
    val outboundPackageName: String,
    val inboundPackageName: String,
    val departingCard: Map<String, Any?>?,
    val returningCard: Map<String, Any?>?,
    val departingRouteLine: String,
    val returningRouteLine: String,
)

internal data class ExtrasSummary(
    val feeRows: List<Map<String, Any?>>,
    val totalSeatFee: Int,
    val seatFeesAmount: BigDecimal,
    val selectedBookingExtras: Set<String>,
    val bookingExtraRows: List<Map<String, Any?>>,
    val totalBookingExtrasFee: Int,
    val bookingExtrasAmount: BigDecimal,
    val step2ExtrasAmount: BigDecimal,
)

internal data class MoneySummary(
    val outboundLineTotal: BigDecimal,
    val inboundLineTotal: BigDecimal,
    val step1FlightsSubtotal: BigDecimal,
    val step1CombinedPerPerson: BigDecimal,
    val grandTotal: BigDecimal,
    val perPassengerAllInPlain: String,
)

internal data class TravelDates(
    val departingDateDisplay: String,
    val returningDateDisplay: String,
)
