package routes

import data.FlightScheduleRepository
import data.FlightScheduleRepository.FlightScheduleRecord
import data.FlightScheduleRepository.SortKey
import io.ktor.http.Parameters
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import utils.jsMode
import utils.timed

/** Registers `/flights`, `/search-flights`, `/book/review`, `/book/passengers`, `/book/seats`. */
fun Route.flightsRoutes() {
    get("/flights") { call.handleFlightsPage() }
    get("/search-flights") { call.handleSearchFlightsList() }
    get("/book/review") { call.handleBookReview() }
    get("/book/passengers") { call.handleBookPassengers() }
    get("/book/seats") { call.handleBookSeats() }
}

/** Simple static “Flights” page so the header link does not clash with search results. */
private suspend fun ApplicationCall.handleFlightsPage() {
    timed("T0_flights_page", jsMode()) {
        val pebble = getEngine()
        val model = mapOf("title" to "Flights")
        val template = pebble.getTemplate("flights/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

/** Parses query string, loads flights, sorts, pages, renders `flights/step-1-search-results/index.peb`. */
private suspend fun ApplicationCall.handleSearchFlightsList() {
    timed("T0_search_flights_list", jsMode()) {
        val pebble = getEngine()
        val queryParams = request.queryParameters

        val fromRaw = queryParams["from"].orEmpty()
        val toRaw = queryParams["to"].orEmpty()
        val departRaw = queryParams["depart"].orEmpty()
        val originCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
        val destCode = FlightScheduleRepository.resolveAirportCode(toRaw)

        val departDate =
            if (departRaw.isNotBlank()) {
                runCatching { LocalDate.parse(departRaw) }.getOrNull()
            } else {
                null
            }

        val sortKey = parseSortKey(queryParams["sort"])
        val ascending =
            if (sortKey == SortKey.RECOMMENDED) {
                true
            } else {
                queryParams["order"] != "desc"
            }
        val cabinRaw = queryParams["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
        val page = queryParams["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        val baseParams = buildBaseParams(queryParams, fromRaw, toRaw, departRaw)

        val paged =
            if (originCode != null && destCode != null && departDate != null) {
                FlightScheduleRepository.search(
                    originCode = originCode,
                    destCode = destCode,
                    depart = departDate,
                    sort = sortKey,
                    ascending = ascending,
                    page = page,
                    pageSize = 10,
                )
            } else {
                FlightScheduleRepository.PagedResult(emptyList(), 0, 1, 10, 1)
            }

        val flightCards = paged.rows.map { row -> flightCardMap(row, cabinRaw) }

        val carouselAnchor = departDate ?: LocalDate.now()
        val carouselDays = buildCarouselDays(carouselAnchor, baseParams)
        val weekPrev = carouselAnchor.minusDays(7)
        val weekNext = carouselAnchor.plusDays(7)
        val sortLinks = buildSortLinks(baseParams, sortKey, ascending)
        val pager = buildPager(paged, baseParams, sortKey, ascending)

        val orderToggleHref: String?
        val orderToggleLabel: String?
        val orderToggleHint: String?
        if (sortKey == SortKey.RECOMMENDED) {
            orderToggleHref = null
            orderToggleLabel = null
            orderToggleHint = null
        } else {
            orderToggleLabel = if (ascending) "Ascending" else "Descending"
            orderToggleHint = if (ascending) "Show highest first" else "Show lowest first"
            orderToggleHref =
                flightsHref(
                    baseParams +
                        mapOf(
                            "sort" to sortKey.toParam(),
                            "order" to if (ascending) "desc" else "asc",
                            "page" to "1",
                        ),
                )
        }

        val cabinLabel =
            when (cabinRaw) {
                "first" -> "First class"
                "business" -> "Business"
                else -> "Economy"
            }
        val isFirstCabin = cabinRaw == "first"
        val isBusinessCabin = cabinRaw == "business"
        val isEconomyCabin = !isFirstCabin && !isBusinessCabin
        val cabinBannerIsFirst = cabinRaw == "first"

        val leg = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
        val inboundLeg = leg == "inbound"
        val obFrom = queryParams["obFrom"].orEmpty()
        val obTo = queryParams["obTo"].orEmpty()
        val obDepart = queryParams["obDepart"].orEmpty()
        val outboundSummaryLine =
            if (inboundLeg && obFrom.isNotBlank() && obTo.isNotBlank() && obDepart.isNotBlank()) {
                "$obFrom → $obTo · $obDepart"
            } else {
                ""
            }
        val hasSearch = originCode != null && destCode != null && departDate != null
        val tripReturn = queryParams["trip"].equals("return", ignoreCase = true)
        val backToOutboundHref =
            if (hasSearch && inboundLeg && tripReturn && outboundSummaryLine.isNotBlank()) {
                flightsHref(buildOutboundLegSearchParams(queryParams))
            } else {
                ""
            }
        // On inbound results, keep a return date for `data-search-return` when raw `return` is blank.
        val returnDateForFareNav =
            when {
                queryParams["return"].orEmpty().isNotBlank() -> queryParams["return"].orEmpty()
                inboundLeg && departRaw.isNotBlank() -> departRaw
                else -> ""
            }
        val preservedOutboundFlightId =
            if (inboundLeg) {
                queryParams["obFlight"].orEmpty().ifBlank { queryParams["flight"].orEmpty() }
            } else {
                ""
            }
        val preservedOutboundFare =
            if (inboundLeg) {
                queryParams["obFare"].orEmpty().ifBlank { queryParams["fare"].orEmpty() }
            } else {
                ""
            }

        val model =
            mapOf(
                "title" to "Search flights",
                "hasSearch" to hasSearch,
                "originCity" to (if (originCode != null) FlightScheduleRepository.cityForCode(originCode) else ""),
                "destCity" to (if (destCode != null) FlightScheduleRepository.cityForCode(destCode) else ""),
                "fromRaw" to fromRaw,
                "toRaw" to toRaw,
                "departRaw" to departRaw,
                "trip" to queryParams["trip"].orEmpty(),
                "cabinClass" to queryParams["cabinClass"].orEmpty(),
                "adults" to queryParams["adults"].orEmpty(),
                "children" to queryParams["children"].orEmpty(),
                "returnRaw" to queryParams["return"].orEmpty(),
                "returnDateForFareNav" to returnDateForFareNav,
                "preservedOutboundFlightId" to preservedOutboundFlightId,
                "preservedOutboundFare" to preservedOutboundFare,
                "obFromRaw" to obFrom,
                "obToRaw" to obTo,
                "obDepartRaw" to obDepart,
                "outboundPriceRaw" to queryParams["outboundPrice"].orEmpty(),
                "inboundLeg" to inboundLeg,
                "outboundSummaryLine" to outboundSummaryLine,
                "backToOutboundHref" to backToOutboundHref,
                "totalResults" to paged.totalCount,
                "flightCards" to flightCards,
                "carouselDays" to carouselDays,
                "weekPrevHref" to
                    flightsHref(
                        baseParams + mapOf("depart" to weekPrev.toString(), "page" to "1"),
                    ),
                "weekNextHref" to
                    flightsHref(
                        baseParams + mapOf("depart" to weekNext.toString(), "page" to "1"),
                    ),
                "sortRecommendedCurrent" to (sortKey == SortKey.RECOMMENDED),
                "sortDepartureCurrent" to (sortKey == SortKey.DEPARTURE),
                "sortArrivalCurrent" to (sortKey == SortKey.ARRIVAL),
                "sortDurationCurrent" to (sortKey == SortKey.DURATION),
                "sortFareCurrent" to (sortKey == SortKey.FARE),
                "sortStopsCurrent" to (sortKey == SortKey.STOPS),
                "sortLinkRecommended" to sortLinks[SortKey.RECOMMENDED],
                "sortLinkDeparture" to sortLinks[SortKey.DEPARTURE],
                "sortLinkArrival" to sortLinks[SortKey.ARRIVAL],
                "sortLinkDuration" to sortLinks[SortKey.DURATION],
                "sortLinkFare" to sortLinks[SortKey.FARE],
                "sortLinkStops" to sortLinks[SortKey.STOPS],
                "orderToggleHref" to orderToggleHref,
                "orderToggleLabel" to orderToggleLabel,
                "orderToggleHint" to orderToggleHint,
                "cabinLabel" to cabinLabel,
                "cabinBannerIsFirst" to cabinBannerIsFirst,
                "isEconomyCabin" to isEconomyCabin,
                "isBusinessCabin" to isBusinessCabin,
                "isFirstCabin" to isFirstCabin,
                "pagerPages" to pager,
                "showPager" to pager.isNotEmpty(),
            )

        val template = pebble.getTemplate("flights/step-1-search-results/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private val BOOKING_QUERY_KEYS =
    listOf(
        "from",
        "to",
        "depart",
        "trip",
        "return",
        "cabinClass",
        "adults",
        "children",
        "leg",
        "obFrom",
        "obTo",
        "obDepart",
        "outboundPrice",
        /** Outbound flight id + fare tier (return trips; distinct from inbound `flight` / `fare`). */
        "obFlight",
        "obFare",
        "fare",
        "flight",
        "price",
        "segDep",
        "segArr",
        "segDur",
        "segFlights",
        "segArrPlus",
        "segOrig",
        "segDest",
    )

/** Preserves booking state across /book/… steps (omit highlight query when continuing). */
private fun bookingParamsMap(queryParams: Parameters): LinkedHashMap<String, String> {
    val preservedParams = LinkedHashMap<String, String>()
    for (queryKey in BOOKING_QUERY_KEYS) {
        queryParams[queryKey]?.takeIf { it.isNotBlank() }?.let { preservedValue ->
            preservedParams[queryKey] = preservedValue
        }
    }
    return preservedParams
}

private fun bookingHref(
    path: String,
    queryParams: Parameters,
): String {
    val preservedParams = bookingParamsMap(queryParams)
    if (preservedParams.isEmpty()) return path
    val encode: (String) -> String = { value ->
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val queryString = preservedParams.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
    return "$path?$queryString"
}

private fun findRecordForRouteAndFlightId(
    fromRaw: String,
    toRaw: String,
    departRaw: String,
    flightId: String,
): FlightScheduleRecord? {
    if (flightId.isBlank()) return null
    val origin = FlightScheduleRepository.resolveAirportCode(fromRaw) ?: return null
    val dest = FlightScheduleRepository.resolveAirportCode(toRaw) ?: return null
    val date = runCatching { LocalDate.parse(departRaw) }.getOrNull() ?: return null
    val searchPage =
        FlightScheduleRepository.search(
            originCode = origin,
            destCode = dest,
            depart = date,
            sort = SortKey.RECOMMENDED,
            ascending = true,
            page = 1,
            pageSize = 400,
        )
    return searchPage.rows.find { row ->
        val rowDomId =
            encAttr(
                "${row.originCode}-${row.destCode}-${row.departDate}-${row.legFlightNumbers.joinToString("-")}-${row.departTime}",
            )
        rowDomId == flightId
    }
}

private fun findRecordForBooking(queryParams: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = queryParams["from"].orEmpty(),
        toRaw = queryParams["to"].orEmpty(),
        departRaw = queryParams["depart"].orEmpty(),
        flightId = queryParams["flight"].orEmpty(),
    )

private fun findOutboundRecordForBooking(queryParams: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = queryParams["obFrom"].orEmpty(),
        toRaw = queryParams["obTo"].orEmpty(),
        departRaw = queryParams["obDepart"].orEmpty(),
        flightId = queryParams["obFlight"].orEmpty(),
    )

/** Human label e.g. `Economy Light`, `First Flex`. */
private fun farePackageDisplayName(
    tierRaw: String,
    cabinRaw: String,
): String {
    val tier = tierRaw.lowercase(Locale.UK).trim()
    val cabin = cabinRaw.lowercase(Locale.UK).trim()
    return when (tier) {
        "light" ->
            when (cabin) {
                "business" -> "Business Light"
                "first" -> "First Light"
                else -> "Economy Light"
            }
        "essential" ->
            when (cabin) {
                "business" -> "Business Essential"
                "first" -> "First Essential"
                else -> "Economy Essential"
            }
        "flex" ->
            when (cabin) {
                "business" -> "Business Flex"
                "first" -> "First Flex"
                else -> "Economy Flex"
            }
        else -> tierRaw.replaceFirstChar { it.uppercaseChar() }
    }
}

private fun backToFlightSearchHref(queryParams: Parameters): String {
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    val departRaw = queryParams["depart"].orEmpty()
    val legNorm = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
    val fromCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
    val toCode = FlightScheduleRepository.resolveAirportCode(toRaw)
    val obFromCode = FlightScheduleRepository.resolveAirportCode(queryParams["obFrom"].orEmpty())
    val obToCode = FlightScheduleRepository.resolveAirportCode(queryParams["obTo"].orEmpty())
    val inferredInbound =
        legNorm.isBlank() &&
            queryParams["trip"].equals("return", ignoreCase = true) &&
            fromCode != null &&
            toCode != null &&
            obFromCode != null &&
            obToCode != null &&
            fromCode == obToCode &&
            toCode == obFromCode
    val isInboundLeg = legNorm == "inbound" || inferredInbound
    return if (isInboundLeg) {
        val outboundParams = LinkedHashMap(buildBaseParams(queryParams, fromRaw, toRaw, departRaw))
        outboundParams["page"] = "1"
        if (!queryParams["leg"].equals("inbound", ignoreCase = true)) {
            outboundParams["leg"] = "inbound"
        }
        flightsHref(outboundParams)
    } else {
        flightsHref(buildBaseParams(queryParams, fromRaw, toRaw, departRaw) + mapOf("page" to "1"))
    }
}

/** Inbound flight list with the chosen outbound flight + fare preserved (matches client inbound search URL). */
private fun inboundSearchResultsHref(queryParams: Parameters): String {
    val inboundParams = LinkedHashMap<String, String>()
    val fromRaw = queryParams["from"].orEmpty()
    val toRaw = queryParams["to"].orEmpty()
    val departRaw = queryParams["depart"].orEmpty()
    if (fromRaw.isNotBlank()) inboundParams["from"] = fromRaw
    if (toRaw.isNotBlank()) inboundParams["to"] = toRaw
    if (departRaw.isNotBlank()) inboundParams["depart"] = departRaw
    inboundParams["trip"] = "return"
    inboundParams["return"] = ""
    inboundParams["leg"] = "inbound"
    inboundParams["page"] = "1"
    queryParams["cabinClass"]?.takeIf { it.isNotBlank() }?.let { inboundParams["cabinClass"] = it }
    queryParams["adults"]?.takeIf { it.isNotBlank() }?.let { inboundParams["adults"] = it }
    queryParams["children"]?.takeIf { it.isNotBlank() }?.let { inboundParams["children"] = it }
    queryParams["obFrom"]?.takeIf { it.isNotBlank() }?.let { inboundParams["obFrom"] = it }
    queryParams["obTo"]?.takeIf { it.isNotBlank() }?.let { inboundParams["obTo"] = it }
    queryParams["obDepart"]?.takeIf { it.isNotBlank() }?.let { inboundParams["obDepart"] = it }
    queryParams["obFlight"]?.takeIf { it.isNotBlank() }?.let { inboundParams["obFlight"] = it }
    queryParams["obFare"]?.takeIf { it.isNotBlank() }?.let { inboundParams["obFare"] = it }
    queryParams["outboundPrice"]?.takeIf { it.isNotBlank() }?.let { inboundParams["outboundPrice"] = it }
    queryParams["obFlight"]?.takeIf { it.isNotBlank() }?.let { inboundParams["flight"] = it }
    queryParams["obFare"]?.takeIf { it.isNotBlank() }?.let { inboundParams["fare"] = it }
    return flightsHref(inboundParams)
}

private fun effectiveFareTier(
    tierRaw: String,
    cabinRaw: String,
): String {
    val normalizedTier = tierRaw.lowercase(Locale.UK).trim()
    val isFirstCabin = cabinRaw == "first"
    return when {
        isFirstCabin -> "flex"
        normalizedTier.isBlank() -> "flex"
        else -> normalizedTier
    }
}

private fun moneyForTier(
    fares: Map<String, BigDecimal>,
    tier: String,
    cabinRaw: String,
): BigDecimal =
    when (tier) {
        "light" -> fares.getValue("light")
        "essential" -> fares.getValue("essential")
        "flex" -> fares.getValue("flex")
        else -> fares.getValue("from")
    }

/** After fare selection: recap still counts as step 1 (Choose flights) before passengers. */
private suspend fun ApplicationCall.handleBookReview() {
    timed("T0_book_review", jsMode()) {
        val pebble = getEngine()
        val queryParams = request.queryParameters
        val cabinRaw = queryParams["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
        val tierRawInbound = queryParams["fare"].orEmpty().lowercase(Locale.UK).trim()
        val inboundRow = findRecordForBooking(queryParams)
        if (inboundRow == null) {
            val model =
                mapOf(
                    "title" to "Selection not found",
                    "backHref" to backToFlightSearchHref(queryParams),
                )
            val template = pebble.getTemplate("flights/book-review-missing.peb")
            val writer = StringWriter()
            fullEvaluate(template, writer, model)
            respondText(writer.toString(), ContentType.Text.Html)
            return@timed
        }

        val tripReturn = queryParams["trip"].equals("return", ignoreCase = true)
        val outboundRow = findOutboundRecordForBooking(queryParams)
        val isDualLegReview = tripReturn && outboundRow != null

        val isEconomyCabin = cabinRaw != "business" && cabinRaw != "first"
        val isBusinessCabin = cabinRaw == "business"
        val isFirstCabin = cabinRaw == "first"

        val highlight = queryParams["highlight"] == "1"
        val continuePassengersHref = bookingHref("/book/passengers", queryParams)

        val departingCard = flightCardMap(if (isDualLegReview) outboundRow!! else inboundRow, cabinRaw)
        val returningCard = if (isDualLegReview) flightCardMap(inboundRow, cabinRaw) else null

        val inboundFares = cabinFareSet(inboundRow, cabinRaw)
        val inboundTier = effectiveFareTier(tierRawInbound, cabinRaw)
        val inboundPrice = moneyForTier(inboundFares, inboundTier, cabinRaw)
        val inboundPackageName = farePackageDisplayName(inboundTier, cabinRaw)

        val outboundFares = if (isDualLegReview) cabinFareSet(outboundRow!!, cabinRaw) else inboundFares
        val outboundTierRaw = queryParams["obFare"].orEmpty().lowercase(Locale.UK).trim()
        val outboundTier = effectiveFareTier(outboundTierRaw, cabinRaw)
        val outboundPrice = moneyForTier(outboundFares, outboundTier, cabinRaw)
        val outboundPackageName = farePackageDisplayName(outboundTier, cabinRaw)

        val departingTier = if (isDualLegReview) outboundTier else inboundTier
        val departingPackageName = if (isDualLegReview) outboundPackageName else inboundPackageName
        val departingPrice = if (isDualLegReview) outboundPrice else inboundPrice

        val departingRouteLine =
            if (isDualLegReview) {
                routeCityPairLine(queryParams["obFrom"].orEmpty(), queryParams["obTo"].orEmpty())
            } else {
                routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
            }
        val returningRouteLine =
            if (isDualLegReview) {
                routeCityPairLine(queryParams["from"].orEmpty(), queryParams["to"].orEmpty())
            } else {
                ""
            }

        val selectAnotherDepartingFlightHref =
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
        val selectAnotherDepartingFareHref = selectAnotherDepartingFlightHref

        val selectAnotherReturningFlightHref = if (isDualLegReview) inboundSearchResultsHref(queryParams) else ""
        val selectAnotherReturningFareHref = selectAnotherReturningFlightHref

        val model =
            mapOf(
                "title" to "Review your flights",
                "isDualLegReview" to isDualLegReview,
                "departingCard" to departingCard,
                "returningCard" to returningCard,
                "isEconomyCabin" to isEconomyCabin,
                "isBusinessCabin" to isBusinessCabin,
                "isFirstCabin" to isFirstCabin,
                "departingTier" to departingTier,
                "returningTier" to (if (isDualLegReview) inboundTier else ""),
                "departingPackageName" to departingPackageName,
                "returningPackageName" to (if (isDualLegReview) inboundPackageName else ""),
                "departingPrice" to formatMoney(departingPrice),
                "returningPrice" to (if (isDualLegReview) formatMoney(inboundPrice) else ""),
                "departingRouteLine" to departingRouteLine,
                "returningRouteLine" to returningRouteLine,
                "continuePassengersHref" to continuePassengersHref,
                "selectAnotherDepartingFlightHref" to selectAnotherDepartingFlightHref,
                "selectAnotherDepartingFareHref" to selectAnotherDepartingFareHref,
                "selectAnotherReturningFlightHref" to selectAnotherReturningFlightHref,
                "selectAnotherReturningFareHref" to selectAnotherReturningFareHref,
                "highlightSelection" to highlight,
            )
        val template = pebble.getTemplate("flights/book-review/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private fun routeCityPairLine(
    fromRaw: String,
    toRaw: String,
): String {
    val originResolved = FlightScheduleRepository.resolveAirportCode(fromRaw) ?: return ""
    val destResolved = FlightScheduleRepository.resolveAirportCode(toRaw) ?: return ""
    return "${FlightScheduleRepository.cityForCode(originResolved)} to ${FlightScheduleRepository.cityForCode(destResolved)}"
}

/** Placeholder step 4 until seat selection is implemented. */
private suspend fun ApplicationCall.handleBookSeats() {
    timed("T0_book_seats", jsMode()) {
        val pebble = getEngine()
        val queryParams = request.queryParameters
        val model =
            mapOf(
                "title" to "Seat and extras",
                "chooseFlightsHref" to backToFlightSearchHref(queryParams),
                "passengersHref" to bookingHref("/book/passengers", queryParams),
            )
        val template = pebble.getTemplate("flights/step-3-seats/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

/** Step 3: passenger details (after `/book/review`). */
private suspend fun ApplicationCall.handleBookPassengers() {
    timed("T0_book_passengers", jsMode()) {
        val pebble = getEngine()
        val queryParams = request.queryParameters
        val fromRaw = queryParams["from"].orEmpty()
        val toRaw = queryParams["to"].orEmpty()
        val departRaw = queryParams["depart"].orEmpty()
        val obFrom = queryParams["obFrom"].orEmpty()
        val obTo = queryParams["obTo"].orEmpty()
        val legNorm = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
        val fromCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
        val toCode = FlightScheduleRepository.resolveAirportCode(toRaw)
        val obFromCode = FlightScheduleRepository.resolveAirportCode(obFrom)
        val obToCode = FlightScheduleRepository.resolveAirportCode(obTo)
        val inferredInbound =
            legNorm.isBlank() &&
                queryParams["trip"].equals("return", ignoreCase = true) &&
                fromCode != null &&
                toCode != null &&
                obFromCode != null &&
                obToCode != null &&
                fromCode == obToCode &&
                toCode == obFromCode
        val isInboundLeg = legNorm == "inbound" || inferredInbound
        val backToChooseFlightsHref =
            if (isInboundLeg) {
                val returnSearchParams = LinkedHashMap(buildBaseParams(queryParams, fromRaw, toRaw, departRaw))
                returnSearchParams["page"] = "1"
                if (!queryParams["leg"].equals("inbound", ignoreCase = true)) {
                    returnSearchParams["leg"] = "inbound"
                }
                flightsHref(returnSearchParams)
            } else {
                flightsHref(
                    buildBaseParams(queryParams, fromRaw, toRaw, departRaw) + mapOf("page" to "1"),
                )
            }

        val adultsN = queryParams["adults"]?.toIntOrNull()?.coerceIn(1, 9) ?: 1
        val childrenN = queryParams["children"]?.toIntOrNull()?.coerceIn(0, 8) ?: 0
        val passengerRows = buildPassengerRowModels(adultsN, childrenN)

        val segDep = queryParams["segDep"].orEmpty()
        val segArr = queryParams["segArr"].orEmpty()
        val segDur = queryParams["segDur"].orEmpty()
        val segFlights = queryParams["segFlights"].orEmpty()
        val segOrig = queryParams["segOrig"].orEmpty()
        val segDest = queryParams["segDest"].orEmpty()
        val segArrPlus = queryParams["segArrPlus"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val hasFlightDetail = segDep.isNotBlank() && segArr.isNotBlank()

        val logged = loggedIn()
        val session = logged.session
        val membershipValue =
            if (logged.logged_in && session != null) {
                String.format(Locale.UK, "GA%06d", session.id)
            } else {
                ""
            }

        val thisUri = request.local.uri
        val loginHref =
            "/login?returnUrl=" + URLEncoder.encode(thisUri, StandardCharsets.UTF_8)
        val continueSeatsHref = bookingHref("/book/seats", queryParams)

        val model =
            mapOf(
                "title" to "Passenger details",
                "fromRaw" to fromRaw,
                "toRaw" to toRaw,
                "departRaw" to departRaw,
                "returnRaw" to queryParams["return"].orEmpty(),
                "trip" to queryParams["trip"].orEmpty(),
                "cabinClass" to queryParams["cabinClass"].orEmpty(),
                "adults" to adultsN.toString(),
                "children" to childrenN.toString(),
                "fare" to queryParams["fare"].orEmpty(),
                "flight" to queryParams["flight"].orEmpty(),
                "price" to queryParams["price"].orEmpty(),
                "backToChooseFlightsHref" to backToChooseFlightsHref,
                "passengerRows" to passengerRows,
                "hasFlightDetail" to hasFlightDetail,
                "segDep" to segDep,
                "segArr" to segArr,
                "segDur" to segDur,
                "segFlights" to segFlights,
                "segOrig" to segOrig,
                "segDest" to segDest,
                "segArrPlus" to segArrPlus,
                "loginHref" to loginHref,
                "membershipValue" to membershipValue,
                "membershipFilled" to (logged.logged_in && session != null),
                "continueSeatsHref" to continueSeatsHref,
            )
        val template = pebble.getTemplate("flights/step-2-passengers/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

/** Per-passenger row: global `slot`, screen-reader `heading`, wireframe `badgeTier`. */
private fun buildPassengerRowModels(
    adults: Int,
    children: Int,
): List<Map<String, Any>> {
    val rows = ArrayList<Map<String, Any>>()
    var slot = 1
    repeat(adults) {
        rows.add(
            mapOf(
                "slot" to slot,
                "heading" to "Adult passenger $slot",
                "kind" to "adult",
                "badgeTier" to "ADULT",
            ),
        )
        slot++
    }
    repeat(children) {
        rows.add(
            mapOf(
                "slot" to slot,
                "heading" to "Child passenger $slot",
                "kind" to "child",
                "badgeTier" to "CHILDREN",
            ),
        )
        slot++
    }
    return rows
}

/** Maps `sort` query value to [SortKey]; unknown values fall back to Recommended. */
private fun parseSortKey(raw: String?): SortKey =
    when (raw?.lowercase(Locale.UK)) {
        "departure" -> SortKey.DEPARTURE
        "arrival" -> SortKey.ARRIVAL
        "duration" -> SortKey.DURATION
        "fare" -> SortKey.FARE
        "stops" -> SortKey.STOPS
        else -> SortKey.RECOMMENDED
    }

/** Reverse of [parseSortKey] for building `sort=` links. */
private fun SortKey.toParam(): String =
    when (this) {
        SortKey.RECOMMENDED -> "recommended"
        SortKey.DEPARTURE -> "departure"
        SortKey.ARRIVAL -> "arrival"
        SortKey.DURATION -> "duration"
        SortKey.FARE -> "fare"
        SortKey.STOPS -> "stops"
    }

/**
 * Preserves search fields when building sort/date/pager links.
 * Uses raw `from`/`to` strings so the browser round-trips exactly what the user submitted.
 */
private fun buildBaseParams(
    queryParams: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): Map<String, String> {
    val preservedParams = LinkedHashMap<String, String>()
    if (fromRaw.isNotBlank()) preservedParams["from"] = fromRaw
    if (toRaw.isNotBlank()) preservedParams["to"] = toRaw
    if (departRaw.isNotBlank()) preservedParams["depart"] = departRaw
    queryParams["trip"]?.takeIf { it.isNotBlank() }?.let { preservedParams["trip"] = it }
    queryParams["cabinClass"]?.takeIf { it.isNotBlank() }?.let { preservedParams["cabinClass"] = it }
    queryParams["adults"]?.takeIf { it.isNotBlank() }?.let { preservedParams["adults"] = it }
    queryParams["children"]?.takeIf { it.isNotBlank() }?.let { preservedParams["children"] = it }
    queryParams["return"]?.takeIf { it.isNotBlank() }?.let { preservedParams["return"] = it }
    queryParams["leg"]?.takeIf { it.isNotBlank() }?.let { preservedParams["leg"] = it }
    queryParams["obFrom"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFrom"] = it }
    queryParams["obTo"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obTo"] = it }
    queryParams["obDepart"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obDepart"] = it }
    /** Inbound results URL carries the chosen outbound flight + tier alongside the inbound route. */
    queryParams["flight"]?.takeIf { it.isNotBlank() }?.let { preservedParams["flight"] = it }
    queryParams["fare"]?.takeIf { it.isNotBlank() }?.let { preservedParams["fare"] = it }
    queryParams["obFlight"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFlight"] = it }
    queryParams["obFare"]?.takeIf { it.isNotBlank() }?.let { preservedParams["obFare"] = it }
    queryParams["outboundPrice"]?.takeIf { it.isNotBlank() }?.let { preservedParams["outboundPrice"] = it }
    return preservedParams
}

/**
 * Outbound leg search for a return trip: `from`/`to`/`depart` are the outbound airports and date;
 * `return` is the inbound travel date. Omits `leg` and `ob*` so the results page is the departing-flight list.
 *
 * On the **inbound** results URL, `return` is often empty because the client clears it when opening the
 * inbound leg; the inbound travel date still appears as `depart`. We copy that into `return` here so
 * outbound cards populate `data-search-return` and fare selection continues to the inbound search
 * instead of `/book/passengers`.
 */
private fun buildOutboundLegSearchParams(queryParams: Parameters): Map<String, String> {
    val outboundParams = LinkedHashMap<String, String>()
    val obFrom = queryParams["obFrom"].orEmpty()
    val obTo = queryParams["obTo"].orEmpty()
    val obDepart = queryParams["obDepart"].orEmpty()
    val leg = queryParams["leg"].orEmpty().lowercase(Locale.UK).trim()
    val departAny = queryParams["depart"].orEmpty()
    val retExplicit = queryParams["return"].orEmpty()
    val returnDate =
        when {
            retExplicit.isNotBlank() -> retExplicit
            leg == "inbound" && departAny.isNotBlank() -> departAny
            else -> ""
        }
    if (obFrom.isNotBlank()) outboundParams["from"] = obFrom
    if (obTo.isNotBlank()) outboundParams["to"] = obTo
    if (obDepart.isNotBlank()) outboundParams["depart"] = obDepart
    if (returnDate.isNotBlank()) outboundParams["return"] = returnDate
    outboundParams["trip"] = "return"
    queryParams["cabinClass"]?.takeIf { it.isNotBlank() }?.let { outboundParams["cabinClass"] = it }
    queryParams["adults"]?.takeIf { it.isNotBlank() }?.let { outboundParams["adults"] = it }
    queryParams["children"]?.takeIf { it.isNotBlank() }?.let { outboundParams["children"] = it }
    outboundParams["page"] = "1"
    return outboundParams
}

/**
 * Builds `/search-flights?…` with UTF-8 percent-encoding.
 * Spaces become `%20` (not `+`) so pasted links and strict parsers behave consistently.
 */
private fun flightsHref(params: Map<String, String>): String {
    if (params.isEmpty()) return "/search-flights"
    val encodeQueryPart: (String) -> String = { part ->
        URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val queryString =
        params.entries.joinToString("&") { (key, value) ->
            "${encodeQueryPart(key)}=${encodeQueryPart(value)}"
        }
    return "/search-flights?$queryString"
}

/** `742` → `"12h 22m"` (total journey / card summary). */
private fun formatDurationMinutes(min: Int): String {
    val hours = min / 60
    val minutes = min % 60
    return "${hours}h ${minutes}m"
}

/** Layover in route details, e.g. `14h 30 min` or `45 min`. */
private fun formatLayoverDuration(min: Int): String {
    val hours = min / 60
    val minutes = min % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes} min"
        hours > 0 -> "${hours}h"
        else -> "${minutes} min"
    }
}

private fun formatMoney(gbp: java.math.BigDecimal): String =
    gbp.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** Always `HH:mm` (e.g. `00:15`, `08:20`) for consistent display. */
private fun formatTime(time: LocalTime): String =
    String.format(Locale.UK, "%02d:%02d", time.hour, time.minute)

/** Economy / business / first tier prices for a schedule row (before column invariants). */
private fun cabinFareSet(row: FlightScheduleRecord, cabinRaw: String): Map<String, BigDecimal> {
    val economyLight = row.priceLight
    val economyEssential = row.priceEssential
    val economyFlex = row.priceFlex
    val fares =
        when (cabinRaw) {
        "business" -> {
            val businessEssential = (economyLight * BigDecimal("5.00")).setScale(2, RoundingMode.HALF_UP)
            val businessFlex =
                (businessEssential + maxOf(BigDecimal("150.00"), economyLight * BigDecimal("1.10")))
                    .setScale(2, RoundingMode.HALF_UP)
            mapOf(
                "from" to businessEssential,
                "light" to businessEssential,
                "essential" to businessEssential,
                "flex" to businessFlex,
            )
        }
        "first" -> {
            val firstFlex = (economyLight * BigDecimal("8.00")).setScale(2, RoundingMode.HALF_UP)
            mapOf(
                "from" to firstFlex,
                "light" to firstFlex,
                "essential" to firstFlex,
                "flex" to firstFlex,
            )
        }
        else ->
            mapOf(
                "from" to economyLight,
                "light" to economyLight,
                "essential" to economyEssential,
                "flex" to economyFlex,
            )
        }
    return enforceFareInvariants(fares, cabinRaw)
}

private fun enforceFareInvariants(
    fares: Map<String, BigDecimal>,
    cabinRaw: String,
): Map<String, BigDecimal> {
    val light = fares.getValue("light")
    val essential = fares.getValue("essential")
    val flex = fares.getValue("flex")
    val fixedEssential = if (essential < light) light else essential
    val fixedFlex = if (flex <= fixedEssential) fixedEssential + BigDecimal("1.00") else flex
    val from =
        when (cabinRaw) {
            "first" -> fixedFlex
            "business" -> fixedEssential
            else -> light
        }
    return mapOf(
        "from" to from.setScale(2, RoundingMode.HALF_UP),
        "light" to light.setScale(2, RoundingMode.HALF_UP),
        "essential" to fixedEssential.setScale(2, RoundingMode.HALF_UP),
        "flex" to fixedFlex.setScale(2, RoundingMode.HALF_UP),
    )
}

private fun flightCardMap(row: FlightScheduleRecord, cabinRaw: String): Map<String, Any?> {
    val legFlightNumbersKey = row.legFlightNumbers.joinToString("-")
    val cardDomId =
        encAttr(
            "${row.originCode}-${row.destCode}-${row.departDate}-${legFlightNumbersKey}-${row.departTime}",
        )
    val arrivalPlusDays = row.arrivalOffsetDays.coerceAtLeast(0)
    val fares = cabinFareSet(row, cabinRaw)
    return mapOf(
        "id" to cardDomId,
        "departTime" to formatTime(row.departTime),
        "arrivalTime" to formatTime(row.arrivalTime),
        "arrivalPlusDays" to arrivalPlusDays,
        "originCode" to row.originCode,
        "destCode" to row.destCode,
        "originFull" to FlightScheduleRepository.airportNameForCode(row.originCode),
        "destFull" to FlightScheduleRepository.airportNameForCode(row.destCode),
        "stopSummary" to stopSummary(row),
        "durationLabel" to formatDurationMinutes(row.durationMinutes),
        "stops" to row.stops,
        "flightNumberChain" to row.legFlightNumbers.joinToString(" → "),
        "fromPrice" to formatMoney(fares.getValue("from")),
        "priceLight" to formatMoney(fares.getValue("light")),
        "priceEssential" to formatMoney(fares.getValue("essential")),
        "priceFlex" to formatMoney(fares.getValue("flex")),
        "routeBlocks" to buildRouteBlocks(row),
        "timelineStops" to row.stopoverCodes.map { mapOf("code" to it) },
    )
}

/** One line under the timeline: direct or stop count only (no airports or layovers). */
private fun stopSummary(row: FlightScheduleRecord): String =
    when (row.stops) {
        0 -> "Direct"
        1 -> "1 stop"
        else -> "${row.stops} stops"
    }

/**
 * Builds segment/connect rows for the Route details panel.
 * Times are local to each airport, and each leg keeps its own flight number.
 *
 * Note: [FlightScheduleRecord.legArrivalOffsetDays] values are cumulative from [departDate],
 * not per-leg increments, so they must not be summed.
 */
private fun buildRouteBlocks(row: FlightScheduleRecord): List<Map<String, Any?>> {
    val legs = row.stops + 1
    val codes = buildList {
        add(row.originCode)
        addAll(row.stopoverCodes)
        add(row.destCode)
    }
    val blocks = mutableListOf<Map<String, Any?>>()
    for (legIndex in 0 until legs) {
        val legDepart = row.legDepartureTimes[legIndex]
        val legArrive = row.legArrivalTimes[legIndex]
        val arrCumulative =
            row.legArrivalOffsetDays.getOrElse(legIndex) { if (legArrive.isBefore(legDepart)) 1 else 0 }
        val depPlusDays =
            if (legIndex == 0) {
                0
            } else {
                row.legArrivalOffsetDays.getOrElse(legIndex - 1) {
                    if (row.legArrivalTimes[legIndex - 1].isBefore(row.legDepartureTimes[legIndex])) 1 else 0
                }
            }
        val arrPlusDays =
            if (legIndex == legs - 1) {
                maxOf(arrCumulative, row.arrivalOffsetDays.coerceAtLeast(0))
            } else {
                arrCumulative
            }
        blocks.add(
            mapOf(
                "kind" to "segment",
                "fromCode" to codes[legIndex],
                "toCode" to codes[legIndex + 1],
                "fromName" to FlightScheduleRepository.airportNameForCode(codes[legIndex]),
                "toName" to FlightScheduleRepository.airportNameForCode(codes[legIndex + 1]),
                "depart" to formatTime(legDepart),
                "depPlusDays" to depPlusDays,
                "arrive" to formatTime(legArrive),
                "arrPlusDays" to arrPlusDays,
                "flight" to row.legFlightNumbers[legIndex],
            ),
        )
        if (legIndex < row.stops) {
            val stopoverAirportCode = codes[legIndex + 1]
            val layoverMinutes = row.stopoverLayoverMinutes.getOrElse(legIndex) { 75 }
            blocks.add(
                mapOf(
                    "kind" to "connect",
                    "airportCode" to stopoverAirportCode,
                    "airportName" to FlightScheduleRepository.airportNameForCode(stopoverAirportCode),
                    "layoverLabel" to formatLayoverDuration(layoverMinutes),
                ),
            )
        }
    }
    return blocks
}

/** Colons break HTML ids; strip them so client `getElementById` still works. */
private fun encAttr(rawId: String): String = rawId.replace(":", "-")

private val dayChipFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/** Seven-day strip around selected date, each linking back with the same search params. */
private fun buildCarouselDays(
    selected: LocalDate,
    base: Map<String, String>,
): List<Map<String, Any?>> {
    val today = LocalDate.now()
    return (-3..3).map { offset ->
        val chipDate = selected.plusDays(offset.toLong())
        val params = base.toMutableMap()
        params["depart"] = chipDate.toString()
        params["page"] = "1"
        val past = chipDate.isBefore(today)
        mapOf(
            "label" to dayChipFmt.format(chipDate),
            "iso" to chipDate.toString(),
            "selected" to (offset == 0),
            "past" to past,
            "href" to if (past) "" else flightsHref(params),
        )
    }
}

/** Href for each sort tab; clicking the active tab toggles asc/desc (except Recommended). */
private fun buildSortLinks(
    base: Map<String, String>,
    current: SortKey,
    ascending: Boolean,
): Map<SortKey, String> {
    return SortKey.entries.associateWith { key ->
        val nextOrder =
            when {
                key == SortKey.RECOMMENDED -> "asc"
                key == current -> if (ascending) "desc" else "asc"
                else -> "asc"
            }
        flightsHref(
            base +
                mapOf(
                    "sort" to key.toParam(),
                    "order" to nextOrder,
                    "page" to "1",
                ),
        )
    }
}

/** Compact numeric pager (max 5 buttons). */
private fun buildPager(
    paged: FlightScheduleRepository.PagedResult,
    base: Map<String, String>,
    sort: SortKey,
    ascending: Boolean,
): List<Map<String, Any?>> {
    if (paged.pageCount <= 1) return emptyList()
    val maxButtons = 5
    var start = (paged.page - maxButtons / 2).coerceAtLeast(1)
    var end = start + maxButtons - 1
    if (end > paged.pageCount) {
        end = paged.pageCount
        start = (end - maxButtons + 1).coerceAtLeast(1)
    }
    val orderStr = if (ascending) "asc" else "desc"
    return (start..end).map { pageNumber ->
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
