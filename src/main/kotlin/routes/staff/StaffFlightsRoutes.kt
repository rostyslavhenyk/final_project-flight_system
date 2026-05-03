package routes.staff

import data.FlightRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.renderTemplate
import utils.jsMode
import utils.timed

private const val DEFAULT_PAGE = 1
private const val DEFAULT_PAGE_SIZE = 50
private const val LARGE_PAGE_SIZE = 100
private const val STAFF_FLIGHTS_PAGER_WINDOW = 5
private val ALLOWED_PAGE_SIZES = setOf(DEFAULT_PAGE_SIZE, LARGE_PAGE_SIZE)

fun Route.staffFlightsRoutes() {
    get("/flights") { call.handleStaffFlights() }
}

private suspend fun ApplicationCall.handleStaffFlights() {
    timed("T4_staff_flights_list", jsMode()) {
        val requestedPage = request.queryParameters["page"]?.toIntOrNull() ?: DEFAULT_PAGE
        val requestedPageSize = request.queryParameters["pageSize"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
        val pageSize =
            if (requestedPageSize in ALLOWED_PAGE_SIZES) {
                requestedPageSize
            } else {
                DEFAULT_PAGE_SIZE
            }
        val flightPage = FlightRepository.pagedFull(requestedPage, pageSize)

        renderTemplate(
            "staff/flights/index.peb",
            mapOf(
                "title" to "Staff Flights",
                "flights" to flightPage.flights,
                "flightPage" to flightPage,
                "pageNumbers" to staffFlightsPageNumbers(flightPage.page, flightPage.pageCount, flightPage.pageSize),
                "pageSizeOptions" to ALLOWED_PAGE_SIZES.sorted(),
                "startItem" to if (flightPage.total == 0L) 0 else ((flightPage.page - 1) * flightPage.pageSize + 1),
                "endItem" to ((flightPage.page - 1) * flightPage.pageSize + flightPage.flights.size),
                "previousPageHref" to
                    if (flightPage.page > 1) {
                        staffFlightsHref(flightPage.page - 1, flightPage.pageSize)
                    } else {
                        null
                    },
                "nextPageHref" to
                    if (flightPage.page < flightPage.pageCount) {
                        staffFlightsHref(flightPage.page + 1, flightPage.pageSize)
                    } else {
                        null
                    },
            ),
        )
    }
}

private fun staffFlightsPageNumbers(
    currentPage: Int,
    pageCount: Int,
    pageSize: Int,
): List<Map<String, Any>> {
    if (pageCount <= 1) return emptyList()

    var start = (currentPage - STAFF_FLIGHTS_PAGER_WINDOW / 2).coerceAtLeast(1)
    var end = start + STAFF_FLIGHTS_PAGER_WINDOW - 1
    if (end > pageCount) {
        end = pageCount
        start = (end - STAFF_FLIGHTS_PAGER_WINDOW + 1).coerceAtLeast(1)
    }

    return (start..end).map { page ->
        mapOf(
            "num" to page,
            "current" to (page == currentPage),
            "href" to staffFlightsHref(page, pageSize),
        )
    }
}

private fun staffFlightsHref(
    page: Int,
    pageSize: Int,
): String = "/staff/flights?page=$page&pageSize=$pageSize"
