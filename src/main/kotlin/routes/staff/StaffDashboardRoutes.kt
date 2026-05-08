package routes.staff

import data.BookingRepository
import data.ChatRepository
import data.PurchaseRepository
import data.TicketRepository
import data.flight.FlightScheduleTemplateRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
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
private const val FOUR_WEEKS = 4
private const val SEATS_PER_FLIGHT = 180
private const val SIMULATED_TAKEN_SEAT_PERCENT = 10
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
    val ranges = dashboardDateRanges(today)
    val bookings = BookingRepository.allFull()
    val soldBookings = bookings.filter { it.booking.status.countsAsSoldBooking() }
    val sales = staffDashboardSales(soldBookings, ranges)
    val tickets = staffDashboardTicketCounts()
    val target = staffDashboardTarget(ranges.fourWeeks)

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
        "flightCountToday" to sales.today.flightCount,
        "flightCountWeek" to sales.week.flightCount,
        "flightCountFourWeeks" to sales.fourWeeks.flightCount,
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
        "unreadChatConversations" to ChatRepository.staffUnreadConversationCount(),
        "salesTarget28DaysPlain" to SALES_TARGET_28_DAYS_GBP.toString(),
        "salesReached28DaysPlain" to formatDashboardMoney(target.sales28Days),
        "salesTargetProgressPercent" to target.progressPercent,
        "salesTargetChartPercent" to target.chartPercent,
        "salesTargetOverflowPercent" to target.overflowPercent,
        "openChatCount" to ChatRepository.getAllOpen().groupBy { it.userId }.size,
    )
}

private fun staffDashboardSales(
    soldBookings: List<data.BookingFull>,
    ranges: DashboardDateRanges,
): StaffDashboardSales {
    val todaySales = soldFlightSales(soldBookings, ranges.today.start, ranges.today.end)
    val weekSales = soldFlightSales(soldBookings, ranges.week.start, ranges.week.end)
    val fourWeekSales = soldFlightSales(soldBookings, ranges.fourWeeks.start, ranges.fourWeeks.end)
    return StaffDashboardSales(todaySales, weekSales, fourWeekSales)
}

private fun staffDashboardTicketCounts(): StaffTicketCounts {
    val tickets = TicketRepository.all()
    val activeTickets = tickets.filter { it.status.isActiveTicketStatus() }
    val userTicketCount = activeTickets.count { it.source.equals("USER", ignoreCase = true) }
    val staffTicketCount = activeTickets.count { it.source.equals("STAFF", ignoreCase = true) }
    return StaffTicketCounts(user = userTicketCount, staff = staffTicketCount)
}

private fun staffDashboardTarget(range: DashboardDateRange): StaffSalesTarget {
    val sales28Days =
        PurchaseRepository
            .all()
            .filter { purchase ->
                purchase.createdAt
                    .epochDate()
                    .isBetweenDates(range.start, range.end)
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

private fun dashboardDateRanges(today: LocalDate): DashboardDateRanges {
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val fourWeekStart = weekStart.minusWeeks((FOUR_WEEKS - 1).toLong())
    return DashboardDateRanges(
        today = DashboardDateRange(today, today),
        week = DashboardDateRange(weekStart, today),
        fourWeeks = DashboardDateRange(fourWeekStart, today),
    )
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
    val rangeFlightCount = maxOf(scheduledFlightCountBetweenDates(startDate, endDate), bookedFlightIds.size)
    val simulatedSoldSeats = ((rangeFlightCount * SEATS_PER_FLIGHT * SIMULATED_TAKEN_SEAT_PERCENT) / PERCENT_MAX)
    val totalCapacity = rangeFlightCount * SEATS_PER_FLIGHT
    val soldSeats = realSoldSeats + simulatedSoldSeats
    val remainingSeats = (totalCapacity - soldSeats).coerceAtLeast(0)
    return SoldFlightSales(
        flightCount = rangeFlightCount,
        realSoldSeats = realSoldSeats,
        simulatedSoldSeats = simulatedSoldSeats,
        soldSeats = soldSeats,
        remainingSeats = remainingSeats,
        soldPercent = percentOf(soldSeats, totalCapacity),
    )
}

private fun scheduledFlightCountBetweenDates(
    startDate: LocalDate,
    endDate: LocalDate,
): Int {
    val templates = transaction { FlightScheduleTemplateRepository.all() }
    if (templates.isEmpty()) return 0

    return generateSequence(startDate) { date ->
        date.plusDays(1).takeIf { !it.isAfter(endDate) }
    }.sumOf { date ->
        val dayOfWeek = date.dayOfWeek.value
        templates.count { template ->
            template.status.equals("scheduled", ignoreCase = true) &&
                template.daysOfWeek
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .contains(dayOfWeek)
        }
    }
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
    val flightCount: Int,
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

private data class DashboardDateRange(
    val start: LocalDate,
    val end: LocalDate,
)

private data class DashboardDateRanges(
    val today: DashboardDateRange,
    val week: DashboardDateRange,
    val fourWeeks: DashboardDateRange,
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
