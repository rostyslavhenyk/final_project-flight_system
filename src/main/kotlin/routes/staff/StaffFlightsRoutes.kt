package routes.staff

import data.FlightRepository
import io.ktor.server.application.*
import io.ktor.server.routing.*
import routes.renderTemplate
import utils.jsMode
import utils.timed
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
        val requestedPageSize =
            request.queryParameters["pageSize"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
        val pageSize =
            if (requestedPageSize in ALLOWED_PAGE_SIZES) {
                requestedPageSize
            } else {
                DEFAULT_PAGE_SIZE
            }
        val query = request.queryParameters["q"]?.trim().orEmpty()
        val flightPage = FlightRepository.searchPagedFull(query, requestedPage, pageSize)

        renderTemplate(
            "staff/flights/index.peb",
            mapOf(
                "title" to "Staff Flights",
                "flights" to flightPage.flights,
                "flightPage" to flightPage,
                "searchQuery" to query,
                "encodedSearchQuery" to query.urlEncode(),
                "pageNumbers" to
                    staffFlightsPageNumbers(
                        currentPage = flightPage.page,
                        pageCount = flightPage.pageCount,
                        pageSize = flightPage.pageSize,
                        query = query,
                    ),
                "pageSizeOptions" to ALLOWED_PAGE_SIZES.sorted(),
                "startItem" to flightPage.startItem(),
                "endItem" to flightPage.endItem(),
                "previousPageHref" to
                    if (flightPage.page > 1) {
                        staffFlightsHref(flightPage.page - 1, flightPage.pageSize, query)
                    } else {
                        null
                    },
                "nextPageHref" to
                    if (flightPage.page < flightPage.pageCount) {
                        staffFlightsHref(flightPage.page + 1, flightPage.pageSize, query)
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
    query: String,
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
            "href" to staffFlightsHref(page, pageSize, query),
        )
    }
}

private fun staffFlightsHref(
    page: Int,
    pageSize: Int,
    query: String,
): String {
    val base = "/staff/flights?page=$page&pageSize=$pageSize"
    if (query.isBlank()) return base
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")
    return "$base&q=$encodedQuery"
}

private fun data.FlightPage.startItem(): Int =
    if (total == 0L) {
        0
    } else {
        (page - 1) * pageSize + 1
    }

private fun data.FlightPage.endItem(): Int = (page - 1) * pageSize + flights.size

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")
