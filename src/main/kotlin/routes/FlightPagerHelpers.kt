package routes

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightSortOption

private const val MAX_PAGER_BUTTONS = 5

/** Compact numeric pager. */
internal fun buildPager(
    paged: FlightSearchRepository.PagedResult,
    base: Map<String, String>,
    sort: FlightSortOption,
    ascending: Boolean,
): List<Map<String, Any?>> {
    if (paged.pageCount <= 1) return emptyList()
    val startAndEnd = pagerBounds(paged)
    val orderStr = if (ascending) "asc" else "desc"
    return (startAndEnd.first..startAndEnd.second).map { pageNumber ->
        mapOf(
            "num" to pageNumber,
            "current" to (pageNumber == paged.page),
            "href" to
                flightsHref(
                    base +
                        mapOf(
                            "sort" to sort.toParam(),
                            "order" to orderStr,
                            "page" to pageNumber.toString(),
                        ),
                ),
        )
    }
}

private fun pagerBounds(paged: FlightSearchRepository.PagedResult): Pair<Int, Int> {
    var start = (paged.page - MAX_PAGER_BUTTONS / 2).coerceAtLeast(1)
    var end = start + MAX_PAGER_BUTTONS - 1
    if (end > paged.pageCount) {
        end = paged.pageCount
        start = (end - MAX_PAGER_BUTTONS + 1).coerceAtLeast(1)
    }
    return start to end
}
