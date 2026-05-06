package routes.flight

import data.flight.FlightSearchRepository
import data.flight.FlightSearchRepository.FlightSortOption
import io.ktor.http.Parameters
import java.time.LocalDate
import java.util.Locale

private const val SEARCH_RESULTS_PAGE_SIZE = 10
private const val DATE_CAROUSEL_STEP_DAYS = 7
private const val DATE_CAROUSEL_DEFAULT_BACK_DAYS = 3
private const val MAX_STOP_FILTER = 2

private data class SearchFlightInputs(
    val queryParams: Parameters,
    val fromRaw: String,
    val toRaw: String,
    val departRaw: String,
    val originCode: String?,
    val destCode: String?,
    val departDate: LocalDate?,
    val sortOption: FlightSortOption,
    val ascending: Boolean,
    val effectiveCabin: String,
    val stopFilter: Int?,
    val page: Int,
)

internal fun searchFlightsModel(queryParams: Parameters): Map<String, Any?> {
    val input = searchFlightInputs(queryParams)
    val baseParams = buildBaseParams(queryParams, input.fromRaw, input.toRaw, input.departRaw, input.effectiveCabin)
    val paged =
        if (input.originCode != null && input.destCode != null && input.departDate != null) {
            FlightSearchRepository.search(
                originCode = input.originCode,
                destCode = input.destCode,
                depart = input.departDate,
                sort = input.sortOption,
                ascending = input.ascending,
                stopFilter = input.stopFilter,
                page = input.page,
                pageSize = SEARCH_RESULTS_PAGE_SIZE,
            )
        } else {
            FlightSearchRepository.PagedResult(emptyList(), 0, 1, SEARCH_RESULTS_PAGE_SIZE, 1)
        }
    val sortLinks = buildSortLinks(baseParams, input.sortOption, input.ascending)
    val carousel = searchCarousel(input, baseParams)
    val leg = searchLegState(input)
    val order = orderToggle(baseParams, input.sortOption, input.ascending)
    return searchFlightsBaseModel(input, paged, leg) +
        searchFlightsNavigationModel(carousel, sortLinks, order, paged, baseParams, input)
}

private fun searchFlightInputs(queryParams: Parameters): SearchFlightInputs {
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    val departRaw = queryParams["depart"].orEmpty()
    val sortOption = parseFlightSortOption(queryParams["sort"])
    val originCode = FlightSearchRepository.resolveAirportCode(fromRaw)
    val destCode = FlightSearchRepository.resolveAirportCode(toRaw)
    val cabinQueryRaw = queryParams["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
    return SearchFlightInputs(
        queryParams = queryParams,
        fromRaw = fromRaw,
        toRaw = toRaw,
        departRaw = departRaw,
        originCode = originCode,
        destCode = destCode,
        departDate = departRaw.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sortOption = sortOption,
        ascending = sortOption == FlightSortOption.Recommended || queryParams["order"] != "desc",
        effectiveCabin = CabinNormalization.normalizedCabinForSearch(cabinQueryRaw, originCode, destCode),
        stopFilter = queryParams["stop"]?.toIntOrNull()?.takeIf { stops -> stops in 0..MAX_STOP_FILTER },
        page = queryParams["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
    )
}

private data class SearchCarousel(
    val days: List<Map<String, Any?>>,
    val weekPrevHref: String,
    val weekNextHref: String,
)

private fun searchCarousel(
    input: SearchFlightInputs,
    baseParams: Map<String, String>,
): SearchCarousel {
    val today = LocalDate.now()
    val selectedDate = input.departDate ?: today
    val requestedDateStart =
        input.queryParams["dateStart"]?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
    val defaultStart = selectedDate.minusDays(DATE_CAROUSEL_DEFAULT_BACK_DAYS.toLong())
    val carouselStart =
        (requestedDateStart ?: defaultStart).coerceAtLeast(today)
    val nextCarouselStart = carouselStart.plusDays(DATE_CAROUSEL_STEP_DAYS.toLong())
    return SearchCarousel(
        days = buildCarouselDays(selectedDate, carouselStart, baseParams),
        weekPrevHref =
            carouselHref(
                baseParams,
                carouselStart.minusDays(DATE_CAROUSEL_STEP_DAYS.toLong()).coerceAtLeast(today),
            ),
        weekNextHref = carouselHref(baseParams, nextCarouselStart),
    )
}

private fun carouselHref(
    baseParams: Map<String, String>,
    dateStart: LocalDate,
): String =
    flightsHref(
        baseParams +
            mapOf(
                "dateStart" to dateStart.toString(),
                "page" to "1",
            ),
    )

private data class SearchLegState(
    val inboundLeg: Boolean,
    val obFrom: String,
    val obTo: String,
    val obDepart: String,
    val outboundSummaryLine: String,
    val backToOutboundHref: String,
    val returnDateForFareNav: String,
    val preservedOutboundFlightId: String,
    val preservedOutboundFare: String,
)

private fun searchLegState(input: SearchFlightInputs): SearchLegState {
    val inboundLeg =
        input.queryParams["leg"]
            .orEmpty()
            .lowercase(Locale.UK)
            .trim() == "inbound"
    val obFrom = input.queryParams["obFrom"].orEmpty()
    val obTo = input.queryParams["obTo"].orEmpty()
    val obDepart = input.queryParams["obDepart"].orEmpty()
    val outboundSummaryLine = outboundSummaryLine(inboundLeg, obFrom, obTo, obDepart)
    val tripReturn = input.queryParams["trip"].equals("return", ignoreCase = true)
    val showBackToOutbound = showBackToOutbound(input, inboundLeg, tripReturn, outboundSummaryLine)
    return SearchLegState(
        inboundLeg = inboundLeg,
        obFrom = obFrom,
        obTo = obTo,
        obDepart = obDepart,
        outboundSummaryLine = outboundSummaryLine,
        backToOutboundHref =
            if (showBackToOutbound) {
                flightsHref(buildOutboundLegSearchParams(input.queryParams))
            } else {
                ""
            },
        returnDateForFareNav = returnDateForFareNav(input.queryParams, inboundLeg, input.departRaw),
        preservedOutboundFlightId = preservedOutboundValue(input.queryParams, inboundLeg, "obFlight", "flight"),
        preservedOutboundFare = preservedOutboundValue(input.queryParams, inboundLeg, "obFare", "fare"),
    )
}

private fun outboundSummaryLine(
    inboundLeg: Boolean,
    obFrom: String,
    obTo: String,
    obDepart: String,
): String =
    if (inboundLeg && hasOutboundSummaryParts(obFrom, obTo, obDepart)) {
        "$obFrom -> $obTo - $obDepart"
    } else {
        ""
    }

private fun showBackToOutbound(
    input: SearchFlightInputs,
    inboundLeg: Boolean,
    tripReturn: Boolean,
    outboundSummaryLine: String,
): Boolean =
    hasCompleteSearch(input) &&
        inboundLeg &&
        tripReturn &&
        outboundSummaryLine.isNotBlank()

private fun hasCompleteSearch(input: SearchFlightInputs): Boolean =
    input.originCode != null && input.destCode != null && input.departDate != null

private fun hasOutboundSummaryParts(
    obFrom: String,
    obTo: String,
    obDepart: String,
): Boolean = obFrom.isNotBlank() && obTo.isNotBlank() && obDepart.isNotBlank()

private fun returnDateForFareNav(
    queryParams: Parameters,
    inboundLeg: Boolean,
    departRaw: String,
): String =
    when {
        queryParams["return"].orEmpty().isNotBlank() -> queryParams["return"].orEmpty()
        inboundLeg && departRaw.isNotBlank() -> departRaw
        else -> ""
    }

private fun preservedOutboundValue(
    queryParams: Parameters,
    inboundLeg: Boolean,
    primaryKey: String,
    fallbackKey: String,
): String =
    if (inboundLeg) {
        queryParams[primaryKey]
            .orEmpty()
            .ifBlank { queryParams[fallbackKey].orEmpty() }
    } else {
        ""
    }

private data class OrderToggle(
    val href: String?,
    val label: String?,
    val hint: String?,
)

private fun orderToggle(
    baseParams: Map<String, String>,
    sortOption: FlightSortOption,
    ascending: Boolean,
): OrderToggle =
    if (sortOption == FlightSortOption.Recommended) {
        OrderToggle(null, null, null)
    } else {
        OrderToggle(
            href =
                flightsHref(
                    baseParams +
                        mapOf(
                            "sort" to sortOption.toParam(),
                            "order" to if (ascending) "desc" else "asc",
                            "page" to "1",
                        ),
                ),
            label = if (ascending) "Ascending" else "Descending",
            hint = if (ascending) "Show highest first" else "Show lowest first",
        )
    }

private fun searchFlightsBaseModel(
    input: SearchFlightInputs,
    paged: FlightSearchRepository.PagedResult,
    leg: SearchLegState,
): Map<String, Any?> {
    val isBusinessCabin = input.effectiveCabin == "business"
    return mapOf(
        "title" to "Search flights",
        "hasSearch" to hasCompleteSearch(input),
        "originCity" to input.originCode?.let { FlightSearchRepository.cityForCode(it) }.orEmpty(),
        "destCity" to input.destCode?.let { FlightSearchRepository.cityForCode(it) }.orEmpty(),
        "fromRaw" to input.fromRaw,
        "toRaw" to input.toRaw,
        "departRaw" to input.departRaw,
        "trip" to input.queryParams["trip"].orEmpty(),
        "cabinClass" to input.effectiveCabin,
        "adults" to input.queryParams["adults"].orEmpty(),
        "children" to input.queryParams["children"].orEmpty(),
        "returnRaw" to input.queryParams["return"].orEmpty(),
        "returnDateForFareNav" to leg.returnDateForFareNav,
        "preservedOutboundFlightId" to leg.preservedOutboundFlightId,
        "preservedOutboundFare" to leg.preservedOutboundFare,
        "obFromRaw" to leg.obFrom,
        "obToRaw" to leg.obTo,
        "obDepartRaw" to leg.obDepart,
        "outboundPriceRaw" to input.queryParams["outboundPrice"].orEmpty(),
        "inboundLeg" to leg.inboundLeg,
        "outboundSummaryLine" to leg.outboundSummaryLine,
        "backToOutboundHref" to leg.backToOutboundHref,
        "totalResults" to paged.totalCount,
        "flightCards" to paged.rows.map { row -> flightCardMap(row, input.effectiveCabin) },
        "cabinLabel" to if (input.effectiveCabin == "business") "Business" else "Economy",
        "stopFilterAllCurrent" to (input.stopFilter == null),
        "stopFilterDirectCurrent" to (input.stopFilter == 0),
        "stopFilterOneCurrent" to (input.stopFilter == 1),
        "stopFilterTwoCurrent" to (input.stopFilter == 2),
        "isEconomyCabin" to !isBusinessCabin,
        "isBusinessCabin" to isBusinessCabin,
    )
}

private fun searchFlightsNavigationModel(
    carousel: SearchCarousel,
    sortLinks: Map<FlightSortOption, String>,
    order: OrderToggle,
    paged: FlightSearchRepository.PagedResult,
    baseParams: Map<String, String>,
    input: SearchFlightInputs,
): Map<String, Any?> =
    mapOf(
        "carouselDays" to carousel.days,
        "weekPrevHref" to carousel.weekPrevHref,
        "weekNextHref" to carousel.weekNextHref,
        "sortRecommendedCurrent" to (input.sortOption == FlightSortOption.Recommended),
        "sortDepartureCurrent" to (input.sortOption == FlightSortOption.DepartureTime),
        "sortArrivalCurrent" to (input.sortOption == FlightSortOption.ArrivalTime),
        "sortDurationCurrent" to (input.sortOption == FlightSortOption.Duration),
        "sortFareCurrent" to (input.sortOption == FlightSortOption.Fare),
        "sortStopsCurrent" to (input.sortOption == FlightSortOption.Stops),
        "sortLinkRecommended" to sortLinks[FlightSortOption.Recommended],
        "sortLinkDeparture" to sortLinks[FlightSortOption.DepartureTime],
        "sortLinkArrival" to sortLinks[FlightSortOption.ArrivalTime],
        "sortLinkDuration" to sortLinks[FlightSortOption.Duration],
        "sortLinkFare" to sortLinks[FlightSortOption.Fare],
        "sortLinkStops" to sortLinks[FlightSortOption.Stops],
        "orderToggleHref" to order.href,
        "orderToggleLabel" to order.label,
        "orderToggleHint" to order.hint,
        "stopFilterAllHref" to stopFilterHref(baseParams, null),
        "stopFilterDirectHref" to stopFilterHref(baseParams, 0),
        "stopFilterOneHref" to stopFilterHref(baseParams, 1),
        "stopFilterTwoHref" to stopFilterHref(baseParams, 2),
        "pagerPages" to buildPager(paged, baseParams, input.sortOption, input.ascending),
        "showPager" to (paged.pageCount > 1),
    )

private fun stopFilterHref(
    baseParams: Map<String, String>,
    stopFilter: Int?,
): String {
    val params = LinkedHashMap(baseParams)
    if (stopFilter == null) {
        params.remove("stop")
    } else {
        params["stop"] = stopFilter.toString()
    }
    params["page"] = "1"
    return flightsHref(params)
}
