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

/** Parses query string, loads flights, sorts, pages, renders `flights/search-results.peb`. */
private suspend fun ApplicationCall.handleSearchFlightsList() {
    timed("T0_search_flights_list", jsMode()) {
        val pebble = getEngine()
        val q = request.queryParameters

        val fromRaw = q["from"].orEmpty()
        val toRaw = q["to"].orEmpty()
        val departRaw = q["depart"].orEmpty()
        val originCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
        val destCode = FlightScheduleRepository.resolveAirportCode(toRaw)

        val departDate =
            if (departRaw.isNotBlank()) {
                runCatching { LocalDate.parse(departRaw) }.getOrNull()
            } else {
                null
            }

        val sortKey = parseSortKey(q["sort"])
        val ascending =
            if (sortKey == SortKey.RECOMMENDED) {
                true
            } else {
                q["order"] != "desc"
            }
        val cabinRaw = q["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
        val page = q["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        val baseParams = buildBaseParams(q, fromRaw, toRaw, departRaw)

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

        val leg = q["leg"].orEmpty().lowercase(Locale.UK).trim()
        val inboundLeg = leg == "inbound"
        val obFrom = q["obFrom"].orEmpty()
        val obTo = q["obTo"].orEmpty()
        val obDepart = q["obDepart"].orEmpty()
        val outboundSummaryLine =
            if (inboundLeg && obFrom.isNotBlank() && obTo.isNotBlank() && obDepart.isNotBlank()) {
                "$obFrom → $obTo · $obDepart"
            } else {
                ""
            }
        val hasSearch = originCode != null && destCode != null && departDate != null
        val tripReturn = q["trip"].equals("return", ignoreCase = true)
        val backToOutboundHref =
            if (hasSearch && inboundLeg && tripReturn && outboundSummaryLine.isNotBlank()) {
                flightsHref(buildOutboundLegSearchParams(q))
            } else {
                ""
            }
        // On inbound results, keep a return date for `data-search-return` when raw `return` is blank.
        val returnDateForFareNav =
            when {
                q["return"].orEmpty().isNotBlank() -> q["return"].orEmpty()
                inboundLeg && departRaw.isNotBlank() -> departRaw
                else -> ""
            }
        val preservedOutboundFlightId =
            if (inboundLeg) {
                q["obFlight"].orEmpty().ifBlank { q["flight"].orEmpty() }
            } else {
                ""
            }
        val preservedOutboundFare =
            if (inboundLeg) {
                q["obFare"].orEmpty().ifBlank { q["fare"].orEmpty() }
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
                "trip" to q["trip"].orEmpty(),
                "cabinClass" to q["cabinClass"].orEmpty(),
                "adults" to q["adults"].orEmpty(),
                "children" to q["children"].orEmpty(),
                "returnRaw" to q["return"].orEmpty(),
                "returnDateForFareNav" to returnDateForFareNav,
                "preservedOutboundFlightId" to preservedOutboundFlightId,
                "preservedOutboundFare" to preservedOutboundFare,
                "obFromRaw" to obFrom,
                "obToRaw" to obTo,
                "obDepartRaw" to obDepart,
                "outboundPriceRaw" to q["outboundPrice"].orEmpty(),
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

        val template = pebble.getTemplate("flights/search-results.peb")
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
private fun bookingParamsMap(q: Parameters): LinkedHashMap<String, String> {
    val m = LinkedHashMap<String, String>()
    for (k in BOOKING_QUERY_KEYS) {
        q[k]?.takeIf { it.isNotBlank() }?.let { m[k] = it }
    }
    return m
}

private fun bookingHref(
    path: String,
    q: Parameters,
): String {
    val m = bookingParamsMap(q)
    if (m.isEmpty()) return path
    val enc: (String) -> String = { s ->
        URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val qs = m.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    return "$path?$qs"
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
    val res =
        FlightScheduleRepository.search(
            originCode = origin,
            destCode = dest,
            depart = date,
            sort = SortKey.RECOMMENDED,
            ascending = true,
            page = 1,
            pageSize = 400,
        )
    return res.rows.find { row ->
        val id =
            encAttr(
                "${row.originCode}-${row.destCode}-${row.departDate}-${row.legFlightNumbers.joinToString("-")}-${row.departTime}",
            )
        id == flightId
    }
}

private fun findRecordForBooking(q: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = q["from"].orEmpty(),
        toRaw = q["to"].orEmpty(),
        departRaw = q["depart"].orEmpty(),
        flightId = q["flight"].orEmpty(),
    )

private fun findOutboundRecordForBooking(q: Parameters): FlightScheduleRecord? =
    findRecordForRouteAndFlightId(
        fromRaw = q["obFrom"].orEmpty(),
        toRaw = q["obTo"].orEmpty(),
        departRaw = q["obDepart"].orEmpty(),
        flightId = q["obFlight"].orEmpty(),
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

private fun backToFlightSearchHref(q: Parameters): String {
    val fromRaw = q["from"].orEmpty()
    val toRaw = q["to"].orEmpty()
    val departRaw = q["depart"].orEmpty()
    val legNorm = q["leg"].orEmpty().lowercase(Locale.UK).trim()
    val fromCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
    val toCode = FlightScheduleRepository.resolveAirportCode(toRaw)
    val obFromCode = FlightScheduleRepository.resolveAirportCode(q["obFrom"].orEmpty())
    val obToCode = FlightScheduleRepository.resolveAirportCode(q["obTo"].orEmpty())
    val inferredInbound =
        legNorm.isBlank() &&
            q["trip"].equals("return", ignoreCase = true) &&
            fromCode != null &&
            toCode != null &&
            obFromCode != null &&
            obToCode != null &&
            fromCode == obToCode &&
            toCode == obFromCode
    val isInboundLeg = legNorm == "inbound" || inferredInbound
    return if (isInboundLeg) {
        val m = LinkedHashMap(buildBaseParams(q, fromRaw, toRaw, departRaw))
        m["page"] = "1"
        if (!q["leg"].equals("inbound", ignoreCase = true)) {
            m["leg"] = "inbound"
        }
        flightsHref(m)
    } else {
        flightsHref(buildBaseParams(q, fromRaw, toRaw, departRaw) + mapOf("page" to "1"))
    }
}

/** Inbound flight list with the chosen outbound flight + fare preserved (matches client inbound search URL). */
private fun inboundSearchResultsHref(q: Parameters): String {
    val m = LinkedHashMap<String, String>()
    val fromRaw = q["from"].orEmpty()
    val toRaw = q["to"].orEmpty()
    val departRaw = q["depart"].orEmpty()
    if (fromRaw.isNotBlank()) m["from"] = fromRaw
    if (toRaw.isNotBlank()) m["to"] = toRaw
    if (departRaw.isNotBlank()) m["depart"] = departRaw
    m["trip"] = "return"
    m["return"] = ""
    m["leg"] = "inbound"
    m["page"] = "1"
    q["cabinClass"]?.takeIf { it.isNotBlank() }?.let { m["cabinClass"] = it }
    q["adults"]?.takeIf { it.isNotBlank() }?.let { m["adults"] = it }
    q["children"]?.takeIf { it.isNotBlank() }?.let { m["children"] = it }
    q["obFrom"]?.takeIf { it.isNotBlank() }?.let { m["obFrom"] = it }
    q["obTo"]?.takeIf { it.isNotBlank() }?.let { m["obTo"] = it }
    q["obDepart"]?.takeIf { it.isNotBlank() }?.let { m["obDepart"] = it }
    q["obFlight"]?.takeIf { it.isNotBlank() }?.let { m["obFlight"] = it }
    q["obFare"]?.takeIf { it.isNotBlank() }?.let { m["obFare"] = it }
    q["outboundPrice"]?.takeIf { it.isNotBlank() }?.let { m["outboundPrice"] = it }
    q["obFlight"]?.takeIf { it.isNotBlank() }?.let { m["flight"] = it }
    q["obFare"]?.takeIf { it.isNotBlank() }?.let { m["fare"] = it }
    return flightsHref(m)
}

private fun effectiveFareTier(
    tierRaw: String,
    cabinRaw: String,
): String {
    val t = tierRaw.lowercase(Locale.UK).trim()
    val isFirstCabin = cabinRaw == "first"
    return when {
        isFirstCabin -> "flex"
        t.isBlank() -> "flex"
        else -> t
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
        val q = request.queryParameters
        val cabinRaw = q["cabinClass"].orEmpty().lowercase(Locale.UK).trim()
        val tierRawInbound = q["fare"].orEmpty().lowercase(Locale.UK).trim()
        val inboundRow = findRecordForBooking(q)
        if (inboundRow == null) {
            val model =
                mapOf(
                    "title" to "Selection not found",
                    "backHref" to backToFlightSearchHref(q),
                )
            val template = pebble.getTemplate("flights/book-review-missing.peb")
            val writer = StringWriter()
            fullEvaluate(template, writer, model)
            respondText(writer.toString(), ContentType.Text.Html)
            return@timed
        }

        val tripReturn = q["trip"].equals("return", ignoreCase = true)
        val outboundRow = findOutboundRecordForBooking(q)
        val isDualLegReview = tripReturn && outboundRow != null

        val isEconomyCabin = cabinRaw != "business" && cabinRaw != "first"
        val isBusinessCabin = cabinRaw == "business"
        val isFirstCabin = cabinRaw == "first"

        val highlight = q["highlight"] == "1"
        val continuePassengersHref = bookingHref("/book/passengers", q)

        val departingCard = flightCardMap(if (isDualLegReview) outboundRow!! else inboundRow, cabinRaw)
        val returningCard = if (isDualLegReview) flightCardMap(inboundRow, cabinRaw) else null

        val inboundFares = cabinFareSet(inboundRow, cabinRaw)
        val inboundTier = effectiveFareTier(tierRawInbound, cabinRaw)
        val inboundPrice = moneyForTier(inboundFares, inboundTier, cabinRaw)
        val inboundPackageName = farePackageDisplayName(inboundTier, cabinRaw)

        val outboundFares = if (isDualLegReview) cabinFareSet(outboundRow!!, cabinRaw) else inboundFares
        val outboundTierRaw = q["obFare"].orEmpty().lowercase(Locale.UK).trim()
        val outboundTier = effectiveFareTier(outboundTierRaw, cabinRaw)
        val outboundPrice = moneyForTier(outboundFares, outboundTier, cabinRaw)
        val outboundPackageName = farePackageDisplayName(outboundTier, cabinRaw)

        val departingTier = if (isDualLegReview) outboundTier else inboundTier
        val departingPackageName = if (isDualLegReview) outboundPackageName else inboundPackageName
        val departingPrice = if (isDualLegReview) outboundPrice else inboundPrice

        val departingRouteLine =
            if (isDualLegReview) {
                routeCityPairLine(q["obFrom"].orEmpty(), q["obTo"].orEmpty())
            } else {
                routeCityPairLine(q["from"].orEmpty(), q["to"].orEmpty())
            }
        val returningRouteLine =
            if (isDualLegReview) {
                routeCityPairLine(q["from"].orEmpty(), q["to"].orEmpty())
            } else {
                ""
            }

        val selectAnotherDepartingFlightHref =
            if (isDualLegReview) {
                flightsHref(buildOutboundLegSearchParams(q))
            } else {
                flightsHref(buildBaseParams(q, q["from"].orEmpty(), q["to"].orEmpty(), q["depart"].orEmpty()) + mapOf("page" to "1"))
            }
        val selectAnotherDepartingFareHref = selectAnotherDepartingFlightHref

        val selectAnotherReturningFlightHref = if (isDualLegReview) inboundSearchResultsHref(q) else ""
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
    val o = FlightScheduleRepository.resolveAirportCode(fromRaw) ?: return ""
    val d = FlightScheduleRepository.resolveAirportCode(toRaw) ?: return ""
    return "${FlightScheduleRepository.cityForCode(o)} to ${FlightScheduleRepository.cityForCode(d)}"
}

/** Placeholder step 4 until seat selection is implemented. */
private suspend fun ApplicationCall.handleBookSeats() {
    timed("T0_book_seats", jsMode()) {
        val pebble = getEngine()
        val q = request.queryParameters
        val model =
            mapOf(
                "title" to "Seat and extras",
                "chooseFlightsHref" to backToFlightSearchHref(q),
                "passengersHref" to bookingHref("/book/passengers", q),
            )
        val template = pebble.getTemplate("flights/book-seats.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

/** Step 3: passenger details (after `/book/review`). */
private suspend fun ApplicationCall.handleBookPassengers() {
    timed("T0_book_passengers", jsMode()) {
        val pebble = getEngine()
        val q = request.queryParameters
        val fromRaw = q["from"].orEmpty()
        val toRaw = q["to"].orEmpty()
        val departRaw = q["depart"].orEmpty()
        val obFrom = q["obFrom"].orEmpty()
        val obTo = q["obTo"].orEmpty()
        val legNorm = q["leg"].orEmpty().lowercase(Locale.UK).trim()
        val fromCode = FlightScheduleRepository.resolveAirportCode(fromRaw)
        val toCode = FlightScheduleRepository.resolveAirportCode(toRaw)
        val obFromCode = FlightScheduleRepository.resolveAirportCode(obFrom)
        val obToCode = FlightScheduleRepository.resolveAirportCode(obTo)
        val inferredInbound =
            legNorm.isBlank() &&
                q["trip"].equals("return", ignoreCase = true) &&
                fromCode != null &&
                toCode != null &&
                obFromCode != null &&
                obToCode != null &&
                fromCode == obToCode &&
                toCode == obFromCode
        val isInboundLeg = legNorm == "inbound" || inferredInbound
        val backToChooseFlightsHref =
            if (isInboundLeg) {
                val m = LinkedHashMap(buildBaseParams(q, fromRaw, toRaw, departRaw))
                m["page"] = "1"
                if (!q["leg"].equals("inbound", ignoreCase = true)) {
                    m["leg"] = "inbound"
                }
                flightsHref(m)
            } else {
                flightsHref(
                    buildBaseParams(q, fromRaw, toRaw, departRaw) + mapOf("page" to "1"),
                )
            }

        val adultsN = q["adults"]?.toIntOrNull()?.coerceIn(1, 9) ?: 1
        val childrenN = q["children"]?.toIntOrNull()?.coerceIn(0, 8) ?: 0
        val passengerRows = buildPassengerRowModels(adultsN, childrenN)

        val segDep = q["segDep"].orEmpty()
        val segArr = q["segArr"].orEmpty()
        val segDur = q["segDur"].orEmpty()
        val segFlights = q["segFlights"].orEmpty()
        val segOrig = q["segOrig"].orEmpty()
        val segDest = q["segDest"].orEmpty()
        val segArrPlus = q["segArrPlus"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
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
        val continueSeatsHref = bookingHref("/book/seats", q)

        val model =
            mapOf(
                "title" to "Passenger details",
                "fromRaw" to fromRaw,
                "toRaw" to toRaw,
                "departRaw" to departRaw,
                "returnRaw" to q["return"].orEmpty(),
                "trip" to q["trip"].orEmpty(),
                "cabinClass" to q["cabinClass"].orEmpty(),
                "adults" to adultsN.toString(),
                "children" to childrenN.toString(),
                "fare" to q["fare"].orEmpty(),
                "flight" to q["flight"].orEmpty(),
                "price" to q["price"].orEmpty(),
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
        val template = pebble.getTemplate("flights/book-passengers/index.peb")
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
    q: Parameters,
    fromRaw: String,
    toRaw: String,
    departRaw: String,
): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    if (fromRaw.isNotBlank()) m["from"] = fromRaw
    if (toRaw.isNotBlank()) m["to"] = toRaw
    if (departRaw.isNotBlank()) m["depart"] = departRaw
    q["trip"]?.takeIf { it.isNotBlank() }?.let { m["trip"] = it }
    q["cabinClass"]?.takeIf { it.isNotBlank() }?.let { m["cabinClass"] = it }
    q["adults"]?.takeIf { it.isNotBlank() }?.let { m["adults"] = it }
    q["children"]?.takeIf { it.isNotBlank() }?.let { m["children"] = it }
    q["return"]?.takeIf { it.isNotBlank() }?.let { m["return"] = it }
    q["leg"]?.takeIf { it.isNotBlank() }?.let { m["leg"] = it }
    q["obFrom"]?.takeIf { it.isNotBlank() }?.let { m["obFrom"] = it }
    q["obTo"]?.takeIf { it.isNotBlank() }?.let { m["obTo"] = it }
    q["obDepart"]?.takeIf { it.isNotBlank() }?.let { m["obDepart"] = it }
    /** Inbound results URL carries the chosen outbound flight + tier alongside the inbound route. */
    q["flight"]?.takeIf { it.isNotBlank() }?.let { m["flight"] = it }
    q["fare"]?.takeIf { it.isNotBlank() }?.let { m["fare"] = it }
    q["obFlight"]?.takeIf { it.isNotBlank() }?.let { m["obFlight"] = it }
    q["obFare"]?.takeIf { it.isNotBlank() }?.let { m["obFare"] = it }
    q["outboundPrice"]?.takeIf { it.isNotBlank() }?.let { m["outboundPrice"] = it }
    return m
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
private fun buildOutboundLegSearchParams(q: Parameters): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    val obFrom = q["obFrom"].orEmpty()
    val obTo = q["obTo"].orEmpty()
    val obDepart = q["obDepart"].orEmpty()
    val leg = q["leg"].orEmpty().lowercase(Locale.UK).trim()
    val departAny = q["depart"].orEmpty()
    val retExplicit = q["return"].orEmpty()
    val returnDate =
        when {
            retExplicit.isNotBlank() -> retExplicit
            leg == "inbound" && departAny.isNotBlank() -> departAny
            else -> ""
        }
    if (obFrom.isNotBlank()) m["from"] = obFrom
    if (obTo.isNotBlank()) m["to"] = obTo
    if (obDepart.isNotBlank()) m["depart"] = obDepart
    if (returnDate.isNotBlank()) m["return"] = returnDate
    m["trip"] = "return"
    q["cabinClass"]?.takeIf { it.isNotBlank() }?.let { m["cabinClass"] = it }
    q["adults"]?.takeIf { it.isNotBlank() }?.let { m["adults"] = it }
    q["children"]?.takeIf { it.isNotBlank() }?.let { m["children"] = it }
    m["page"] = "1"
    return m
}

/**
 * Builds `/search-flights?…` with UTF-8 percent-encoding.
 * Spaces become `%20` (not `+`) so pasted links and strict parsers behave consistently.
 */
private fun flightsHref(params: Map<String, String>): String {
    if (params.isEmpty()) return "/search-flights"
    val enc: (String) -> String = { s ->
        URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")
    }
    val qs = params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    return "/search-flights?$qs"
}

/** `742` → `"12h 22m"` (total journey / card summary). */
private fun formatDurationMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return "${h}h ${m}m"
}

/** Layover in route details, e.g. `14h 30 min` or `45 min`. */
private fun formatLayoverDuration(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m} min"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}

private fun formatMoney(gbp: java.math.BigDecimal): String =
    gbp.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** Always `HH:mm` (e.g. `00:15`, `08:20`) for consistent display. */
private fun formatTime(t: LocalTime): String =
    String.format(Locale.UK, "%02d:%02d", t.hour, t.minute)

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
    val fnKey = row.legFlightNumbers.joinToString("-")
    val id =
        encAttr(
            "${row.originCode}-${row.destCode}-${row.departDate}-${fnKey}-${row.departTime}",
        )
    val arrivalPlusDays = row.arrivalOffsetDays.coerceAtLeast(0)
    val fares = cabinFareSet(row, cabinRaw)
    return mapOf(
        "id" to id,
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
    for (i in 0 until legs) {
        val dep = row.legDepartureTimes[i]
        val arr = row.legArrivalTimes[i]
        val arrCumulative =
            row.legArrivalOffsetDays.getOrElse(i) { if (arr.isBefore(dep)) 1 else 0 }
        val depPlusDays =
            if (i == 0) {
                0
            } else {
                row.legArrivalOffsetDays.getOrElse(i - 1) {
                    if (row.legArrivalTimes[i - 1].isBefore(row.legDepartureTimes[i])) 1 else 0
                }
            }
        val arrPlusDays =
            if (i == legs - 1) {
                maxOf(arrCumulative, row.arrivalOffsetDays.coerceAtLeast(0))
            } else {
                arrCumulative
            }
        blocks.add(
            mapOf(
                "kind" to "segment",
                "fromCode" to codes[i],
                "toCode" to codes[i + 1],
                "fromName" to FlightScheduleRepository.airportNameForCode(codes[i]),
                "toName" to FlightScheduleRepository.airportNameForCode(codes[i + 1]),
                "depart" to formatTime(dep),
                "depPlusDays" to depPlusDays,
                "arrive" to formatTime(arr),
                "arrPlusDays" to arrPlusDays,
                "flight" to row.legFlightNumbers[i],
            ),
        )
        if (i < row.stops) {
            val hub = codes[i + 1]
            val layMin = row.stopoverLayoverMinutes.getOrElse(i) { 75 }
            blocks.add(
                mapOf(
                    "kind" to "connect",
                    "airportCode" to hub,
                    "airportName" to FlightScheduleRepository.airportNameForCode(hub),
                    "layoverLabel" to formatLayoverDuration(layMin),
                ),
            )
        }
    }
    return blocks
}

/** Safe id for HTML `id="…"` attributes. */
private fun encAttr(s: String): String = s.replace(":", "-")

private val dayChipFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/** Seven-day strip around selected date, each linking back with the same search params. */
private fun buildCarouselDays(
    selected: LocalDate,
    base: Map<String, String>,
): List<Map<String, Any?>> {
    val today = LocalDate.now()
    return (-3..3).map { offset ->
        val d = selected.plusDays(offset.toLong())
        val params = base.toMutableMap()
        params["depart"] = d.toString()
        params["page"] = "1"
        val past = d.isBefore(today)
        mapOf(
            "label" to dayChipFmt.format(d),
            "iso" to d.toString(),
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
    return (start..end).map { p ->
        mapOf(
            "num" to p,
            "current" to (p == paged.page),
            "href" to
                flightsHref(
                    base +
                        mapOf(
                            "sort" to sort.toParam(),
                            "order" to orderStr,
                            "page" to p.toString(),
                        ),
                ),
        )
    }
}
