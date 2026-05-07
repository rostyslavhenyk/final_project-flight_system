package routes.staff

import data.BookingRepository
import data.FlightRepository
import data.PurchaseRepository
import data.TicketRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.renderTemplate
import utils.jsMode
import utils.timed
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

private const val SALES_TARGET_28_DAYS_GBP = 20_000
private const val PERCENT_MAX = 100
private const val WEEK_DAYS = 7
private const val FOUR_WEEKS = 4
private const val SEATS_PER_FLIGHT = 180
private const val SIMULATED_TAKEN_SEAT_PERCENT = 10
private const val TARGET_WINDOW_DAYS = 28L
private const val HOURS_PER_DAY = 24L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L
private const val ISO_DATE_LENGTH = 10

private val dashboardZone: ZoneId = ZoneId.systemDefault()

fun Route.staffDashboardRoutes() {
    get { call.handleStaffDashboard() }
}

private suspend fun ApplicationCall.handleStaffDashboard() {
    timed("T4_staff_dashboard_load", jsMode()) {
        renderTemplate("staff/dashboard/index.peb", staffDashboardModel())
    }
}

private fun staffDashboardModel(): Map<String, Any?> {
    val today = LocalDate.now(dashboardZone)
    val bookings = BookingRepository.allFull()
    val soldBookings = bookings.filter { it.booking.status.countsAsSoldBooking() }
    val sales = staffDashboardSales(soldBookings, today)
    val tickets = staffDashboardTicketCounts()
    val target = staffDashboardTarget()

    return mapOf(
        "title" to "Staff Dashboard",
        "soldFlightsToday" to sales.today.soldSeats,
        "soldFlightsWeek" to sales.week.soldSeats,
        "soldFlightsFourWeeks" to sales.fourWeeks.soldSeats,
        "soldFlightsRealToday" to sales.today.realSoldSeats,
        "soldFlightsRealWeek" to sales.week.realSoldSeats,
        "soldFlightsRealFourWeeks" to sales.fourWeeks.realSoldSeats,
        "soldFlightsSimulatedToday" to sales.today.simulatedSoldSeats,
        "soldFlightsSimulatedWeek" to sales.week.simulatedSoldSeats,
        "soldFlightsSimulatedFourWeeks" to sales.fourWeeks.simulatedSoldSeats,
        "soldFlightsRemainingToday" to sales.today.remainingSeats,
        "soldFlightsRemainingWeek" to sales.week.remainingSeats,
        "soldFlightsRemainingFourWeeks" to sales.fourWeeks.remainingSeats,
        "soldFlightsPercentToday" to sales.today.soldPercent,
        "soldFlightsPercentWeek" to sales.week.soldPercent,
        "soldFlightsPercentFourWeeks" to sales.fourWeeks.soldPercent,
        "activeUserTickets" to tickets.user,
        "activeStaffTickets" to tickets.staff,
        "activeTicketTotal" to tickets.total,
        "userTicketPercent" to percentOf(tickets.user, tickets.total),
        "salesTarget28DaysPlain" to SALES_TARGET_28_DAYS_GBP.toString(),
        "salesReached28DaysPlain" to formatDashboardMoney(target.sales28Days),
        "salesTargetProgressPercent" to target.progressPercent,
        "salesTargetChartPercent" to target.chartPercent,
        "salesTargetOverflowPercent" to target.overflowPercent,
    )
}

private fun staffDashboardSales(
    soldBookings: List<data.BookingFull>,
    today: LocalDate,
): StaffDashboardSales {
    val todaySales = soldFlightSales(soldBookings, today, today)
    val weekSales = soldFlightSales(soldBookings, today, today.plusDays((WEEK_DAYS - 1).toLong()))
    val fourWeekSales = soldFlightSales(soldBookings, today, today.plusWeeks(FOUR_WEEKS.toLong()).minusDays(1))
    return StaffDashboardSales(todaySales, weekSales, fourWeekSales)
}

private fun staffDashboardTicketCounts(): StaffTicketCounts {
    val tickets = TicketRepository.all()
    val activeTickets = tickets.filter { it.status.isActiveTicketStatus() }
    val userTicketCount = activeTickets.count { it.source.equals("USER", ignoreCase = true) }
    val staffTicketCount = activeTickets.count { it.source.equals("STAFF", ignoreCase = true) }
    return StaffTicketCounts(user = userTicketCount, staff = staffTicketCount)
}

private fun staffDashboardTarget(): StaffSalesTarget {
    val now = Instant.now()
    val sales28Days =
        PurchaseRepository
            .all()
            .filter { purchase ->
                Instant.ofEpochMilli(purchase.createdAt).isAfter(
                    now.minusSeconds(TARGET_WINDOW_DAYS * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE),
                )
            }.sumOf { purchase -> purchase.amount }
    val targetProgressPercent =
        ((sales28Days / SALES_TARGET_28_DAYS_GBP) * PERCENT_MAX)
            .roundToInt()
            .coerceAtLeast(0)
    val targetChartPercent = targetProgressPercent.coerceAtMost(PERCENT_MAX)
    val targetOverflowPercent =
        if (targetProgressPercent > PERCENT_MAX) {
            val overflow = targetProgressPercent % PERCENT_MAX
            if (overflow == 0) PERCENT_MAX else overflow
        } else {
            0
        }
    return StaffSalesTarget(sales28Days, targetProgressPercent, targetChartPercent, targetOverflowPercent)
}

private fun String.isActiveTicketStatus(): Boolean =
    uppercase().replace(" ", "_").replace("-", "_") in setOf("OPEN", "IN_PROGRESS")

private fun String.countsAsSoldBooking(): Boolean =
    uppercase().replace(" ", "_").replace("-", "_") !in setOf("CANCELLED", "CANCELED", "REFUNDED")

private fun String.flightDate(): LocalDate? =
    runCatching { LocalDateTime.parse(this).toLocalDate() }
        .getOrElse {
            runCatching { LocalDate.parse(take(ISO_DATE_LENGTH)) }.getOrNull()
        }

private fun Long.epochDate(): LocalDate = Instant.ofEpochMilli(this).atZone(dashboardZone).toLocalDate()

private fun LocalDate?.isBetweenDates(
    startDate: LocalDate,
    endDate: LocalDate,
): Boolean {
    val date = this ?: return false
    return !date.isBefore(startDate) && !date.isAfter(endDate)
}

private fun soldFlightSales(
    bookings: List<data.BookingFull>,
    startDate: LocalDate,
    endDate: LocalDate,
): SoldFlightSales {
    val realSoldSeats =
        bookings.count {
            it.booking.createdAt
                .epochDate()
                .isBetweenDates(startDate, endDate)
        }
    val bookedFlightIds =
        bookings
            .filter {
                it.flight.departureTime
                    .flightDate()
                    .isBetweenDates(startDate, endDate)
            }.map { it.flight.flightID }
            .toSet()
    val rangeFlightCount = maxOf(FlightRepository.countBetweenDates(startDate, endDate).toInt(), bookedFlightIds.size)
    val simulatedSoldSeats = ((rangeFlightCount * SEATS_PER_FLIGHT * SIMULATED_TAKEN_SEAT_PERCENT) / PERCENT_MAX)
    val totalCapacity = rangeFlightCount * SEATS_PER_FLIGHT
    val soldSeats = realSoldSeats + simulatedSoldSeats
    val remainingSeats = (totalCapacity - soldSeats).coerceAtLeast(0)
    return SoldFlightSales(
        realSoldSeats = realSoldSeats,
        simulatedSoldSeats = simulatedSoldSeats,
        soldSeats = soldSeats,
        remainingSeats = remainingSeats,
        soldPercent = percentOf(soldSeats, totalCapacity),
    )
}

private fun percentOf(
    value: Int,
    total: Int,
): Int =
    if (total <= 0) {
        0
    } else {
        ((value.toDouble() / total) * PERCENT_MAX).roundToInt().coerceIn(0, PERCENT_MAX)
    }

private fun formatDashboardMoney(value: Double): String = "%,.2f".format(value)

private data class SoldFlightSales(
    val realSoldSeats: Int,
    val simulatedSoldSeats: Int,
    val soldSeats: Int,
    val remainingSeats: Int,
    val soldPercent: Int,
)

private data class StaffDashboardSales(
    val today: SoldFlightSales,
    val week: SoldFlightSales,
    val fourWeeks: SoldFlightSales,
)

private data class StaffTicketCounts(
    val user: Int,
    val staff: Int,
) {
    val total: Int = user + staff
}

private data class StaffSalesTarget(
    val sales28Days: Double,
    val progressPercent: Int,
    val chartPercent: Int,
    val overflowPercent: Int,
)
