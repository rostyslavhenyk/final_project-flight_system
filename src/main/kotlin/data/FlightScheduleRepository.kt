package data

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.abs

/** Max rows kept per (route × arrival day offset × arrival hour) before presentation thinning. */
private const val MAX_FLIGHTS_PER_ARRIVAL_HOUR_BUCKET = 15

/** Max results sharing the same stops count and same stopover airport sequence (e.g. all 1-stop via DXB). */
private const val MAX_FLIGHTS_PER_IDENTICAL_STOPOVER_PATTERN = 5

/** Rotate itineraries so the same template can appear morning / afternoon / night. */
private val DAY_PART_ANCHOR_MINUTES = listOf(0, 390, 750)

/** Stagger variants ≥2h apart so thinning to 1h gaps still leaves enough choice. */
private val VARIANT_DEPARTURE_OFFSETS_MINUTES = listOf(0, 120, 240, 360, 480)

private const val MIN_SPACING_MINUTES = 60

/** 05:00–11:59 */
private const val MORNING_START = 5 * 60

private const val MORNING_END = 12 * 60

/** 12:00–17:59 */
private const val AFTERNOON_END = 18 * 60

/**
 * Flight search data for `/search-flights`.
 *
 * **Staff-editable data (CSV):**
 * - [AIRPORTS_CSV] — city names, full airport names, optional search aliases (pipe-separated).
 * - [TEMPLATES_CSV] — one row per *flight pattern* (not per calendar day): route, total gate-to-gate
 *   minutes, stops, stopover airport codes (pipe-separated), merchandising rank,
 *   **arrival offset days** (for +1 at destination vs departure date),
 *   **per-leg** local departure times, local arrival times, and flight numbers (pipe-separated, one
 *   segment per stop + 1). Scheduled times are **local** at each airport; the final arrival time is
 *   the destination-local clock time (not computed by adding duration to origin time).
 *
 * **Still generated in code:**
 * - Light / Essential / Flex prices (deterministic from date + template).
 * - Layover lengths in minutes (deterministic from date + per-leg flight numbers).
 *
 * **Why not one CSV row per day?** In production you store *schedules* and *exceptions* in tables;
 * expanding every route × every day creates huge files. Here, templates + a chosen date produce
 * rows at runtime — staff only edit the template CSV.
 */
object FlightScheduleRepository {

    private val AIRPORTS_CSV = File("data/airports_display.csv")
    private val TEMPLATES_CSV = File("data/flight_schedule_templates.csv")
    private val TIME_FMT = DateTimeFormatter.ofPattern("H:mm")

    enum class SortKey {
        RECOMMENDED,
        DEPARTURE,
        ARRIVAL,
        DURATION,
        FARE,
        STOPS,
    }

    /** One concrete offer for a given origin/destination/date (built from a template). */
    data class FlightScheduleRecord(
        val originCode: String,
        val destCode: String,
        val departDate: LocalDate,
        val departTime: LocalTime,
        val arrivalTime: LocalTime,
        val arrivalOffsetDays: Int,
        val durationMinutes: Int,
        val stops: Int,
        val legDepartureTimes: List<LocalTime>,
        val legArrivalTimes: List<LocalTime>,
        val legArrivalOffsetDays: List<Int>,
        val legFlightNumbers: List<String>,
        val priceLight: BigDecimal,
        val priceEssential: BigDecimal,
        val priceFlex: BigDecimal,
        val recommendedRank: Int,
        val stopoverCodes: List<String>,
        val stopoverLayoverMinutes: List<Int>,
    )

    /** One page of search results plus paging metadata. */
    data class PagedResult(
        val rows: List<FlightScheduleRecord>,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int,
        val pageCount: Int,
    )

    /** Row from [AIRPORTS_CSV] used for display and text-based airport resolution. */
    data class AirportMeta(
        val code: String,
        val city: String,
        val airportName: String,
        val aliases: List<String> = emptyList(),
    )

    /** Internal: one line from [TEMPLATES_CSV] before expanding to a calendar date. */
    private data class FlightTemplate(
        val originCode: String,
        val destCode: String,
        val durationMinutes: Int,
        val stops: Int,
        val stopoverCodes: List<String>,
        val recommendedRankBase: Int,
        val arrivalOffsetDays: Int,
        val legDepartureTimes: List<LocalTime>,
        val legArrivalTimes: List<LocalTime>,
        val legFlightNumbers: List<String>,
    )

    /** Airports loaded from CSV (lazy, once per JVM). */
    private val airports: List<AirportMeta> by lazy { loadAirportsWithFallback() }

    /** Schedule templates loaded from CSV (lazy, once per JVM). */
    private val templates: List<FlightTemplate> by lazy { loadTemplatesWithFallback() }

    /**
     * Backwards-compatible alias for [resolveAirportCode] (used by older call sites).
     */
    fun parseAirportCode(raw: String): String? = resolveAirportCode(raw)

    /**
     * City label for headings (e.g. "Manchester"), from [AIRPORTS_CSV].
     */
    fun cityForCode(code: String?): String {
        if (code.isNullOrBlank()) return "Unknown city"
        return airports.find { it.code.equals(code, ignoreCase = true) }?.city ?: code
    }

    /**
     * Full airport name for route details (e.g. "Manchester Airport"), from [AIRPORTS_CSV].
     */
    fun airportNameForCode(code: String?): String {
        if (code.isNullOrBlank()) return "Unknown airport"
        return airports.find { it.code.equals(code, ignoreCase = true) }?.airportName ?: code
    }

    /**
     * Turn homepage / free text into an IATA code: `City (MAN)`, `MAN`, `manchester`, etc.
     */
    fun resolveAirportCode(raw: String): String? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null
        val fromBrackets = Regex("\\(([A-Z]{3})\\)\$").find(cleaned)?.groupValues?.get(1)
        if (fromBrackets != null) return fromBrackets
        if (cleaned.length == 3 && cleaned.all { it.isLetter() }) return cleaned.uppercase()

        val lowered = cleaned.lowercase()
        val match =
            airports.find { m ->
                m.city.lowercase() == lowered ||
                    m.airportName.lowercase() == lowered ||
                    m.aliases.any { it.lowercase() == lowered } ||
                    m.city.lowercase().startsWith(lowered) ||
                    m.airportName.lowercase().startsWith(lowered)
            }
        return match?.code
    }

    /**
     * Filter by route + date, apply sort, slice one page.
     */
    fun search(
        originCode: String,
        destCode: String,
        depart: LocalDate,
        sort: SortKey,
        ascending: Boolean,
        page: Int,
        pageSize: Int,
    ): PagedResult {
        val routeRows = expandRouteRecordsForDate(depart, originCode, destCode)
        val presentation = applyRoutePresentationRules(routeRows)
        val sorted = sortRecords(presentation, sort, ascending)
        val safePageSize = pageSize.coerceIn(1, 50)
        val pageCount = maxOf(1, (sorted.size + safePageSize - 1) / safePageSize)
        val safePage = page.coerceIn(1, pageCount)
        val from = (safePage - 1) * safePageSize
        val slice = sorted.drop(from).take(safePageSize)
        return PagedResult(slice, sorted.size, safePage, safePageSize, pageCount)
    }

    /**
     * **Recommended:** fewer stops first, then earlier departure, then earlier arrival, then lower
     * Light fare, then shorter gate-to-gate time (typical OTA “best” ordering).
     */
    private fun recommendedComparator(): Comparator<FlightScheduleRecord> =
        compareBy<FlightScheduleRecord> { it.stops }
            .thenBy { it.departTime }
            .thenBy { it.arrivalOffsetDays }
            .thenBy { it.arrivalTime }
            .thenBy { it.priceLight }
            .thenBy { it.durationMinutes }

    /**
     * Order rows for the active sort mode.
     */
    private fun sortRecords(
        rows: List<FlightScheduleRecord>,
        sort: SortKey,
        ascending: Boolean,
    ): List<FlightScheduleRecord> {
        val ordered =
            when (sort) {
                SortKey.RECOMMENDED -> rows.sortedWith(recommendedComparator())
                SortKey.DEPARTURE -> rows.sortedWith(compareBy { it.departTime })
                SortKey.ARRIVAL ->
                    rows.sortedWith(
                        compareBy<FlightScheduleRecord> { it.arrivalOffsetDays }
                            .thenBy { it.arrivalTime },
                    )
                SortKey.DURATION -> rows.sortedWith(compareBy { it.durationMinutes })
                SortKey.FARE -> rows.sortedWith(compareBy { it.priceLight })
                SortKey.STOPS -> rows.sortedWith(compareBy { it.stops })
            }
        return if (ascending) ordered else ordered.reversed()
    }

    /**
     * Expand only templates for [originCode]→[destCode] for [date] (avoids building the full network).
     */
    private fun expandRouteRecordsForDate(
        date: LocalDate,
        originCode: String,
        destCode: String,
    ): List<FlightScheduleRecord> {
        val routeTemplates =
            templates.filter { t ->
                t.originCode.equals(originCode, ignoreCase = true) &&
                    t.destCode.equals(destCode, ignoreCase = true)
            }
        val expanded =
            routeTemplates.flatMap { t ->
                DAY_PART_ANCHOR_MINUTES.flatMapIndexed { anchorIdx, anchorMin ->
                    VARIANT_DEPARTURE_OFFSETS_MINUTES.mapIndexed { variantIdx, offsetMin ->
                        val shift = anchorMin + offsetMin
                        val flatIdx = anchorIdx * VARIANT_DEPARTURE_OFFSETS_MINUTES.size + variantIdx
                        buildTemplateVariantRecord(date, t, flatIdx, shift.toLong())
                    }
                }
            }
        return applyRouteCaps(expanded)
    }

    /** Thin duplicate routings (same hubs) and arrival-hour buckets before UI presentation rules. */
    private fun applyRouteCaps(expanded: List<FlightScheduleRecord>): List<FlightScheduleRecord> {
        val afterDirect = capNonStopByDepartureDayPart(expanded)
        val afterStopover = capIdenticalStopoverPatternConnecting(afterDirect)
        return enforceArrivalHourCap(afterStopover)
    }

    /**
     * Non-stop: at most **one** option per departure day-part (morning / afternoon / night) so
     * directs are not all red-eye clones and match “one AM / one PM / one evening” intent.
     */
    private fun capNonStopByDepartureDayPart(rows: List<FlightScheduleRecord>): List<FlightScheduleRecord> {
        val cmp = recommendedComparator()
        val (direct, conn) = rows.partition { it.stops == 0 }
        val cappedDirect =
            direct
                .groupBy { departureBand(it.departTime) }
                .values
                .mapNotNull { g -> g.minWithOrNull(cmp) }
        return cappedDirect + conn
    }

    /**
     * Connecting flights: at most [MAX_FLIGHTS_PER_IDENTICAL_STOPOVER_PATTERN] per
     * `(stops, stopoverCodes)` (e.g. 1-stop via DXB).
     */
    private fun capIdenticalStopoverPatternConnecting(rows: List<FlightScheduleRecord>): List<FlightScheduleRecord> {
        val cmp = recommendedComparator()
        val directs = rows.filter { it.stops == 0 }
        val capped =
            rows
                .filter { it.stops > 0 }
                .groupBy { "${it.stops}|${it.stopoverCodes.joinToString("|")}" }
                .values
                .flatMap { group -> group.sortedWith(cmp).take(MAX_FLIGHTS_PER_IDENTICAL_STOPOVER_PATTERN) }
        return directs + capped
    }

    /**
     * Build one deterministic variant of a template (same routing, slightly shifted local clock slots).
     * This gives weekly/monthly loops richer schedules without writing thousands of day rows.
     */
    private fun buildTemplateVariantRecord(
        date: LocalDate,
        template: FlightTemplate,
        variantIdx: Int,
        shiftMinutes: Long,
    ): FlightScheduleRecord {
        val shiftedDepartures =
            template.legDepartureTimes.map { snapToFiveMinuteGrid(it.plusMinutes(shiftMinutes)) }
        val shiftedArrivals =
            template.legArrivalTimes.map { snapToFiveMinuteGrid(it.plusMinutes(shiftMinutes)) }
        val routeKey = "${template.originCode}-${template.destCode}-${template.recommendedRankBase}"
        val (diverseDeps, diverseArrs) =
            diversifyItineraryTimes(
                shiftedDepartures,
                shiftedArrivals,
                template.stops,
                variantIdx,
                routeKey,
            )
        val zoned =
            applyAirportLocalZoneConversion(
                departDate = date,
                originCode = template.originCode,
                destCode = template.destCode,
                stopoverCodes = template.stopoverCodes,
                legDeps = diverseDeps,
                legArrs = diverseArrs,
                stops = template.stops,
                templateArrivalOffsetHint = template.arrivalOffsetDays,
            )
        val departTime = zoned.legDepartureTimes.first()
        val arrivalTime = zoned.legArrivalTimes.last()
        val arrivalOffsetDays = zoned.arrivalOffsetDays
        val durationMinutes = zoned.durationMinutes

        val light = generatedPriceLight(date, template, variantIdx)
        val essential = light + BigDecimal("92.00")
        val flex = light + BigDecimal("248.00")
        val dateBump = (date.dayOfMonth % 3)

        return FlightScheduleRecord(
            originCode = template.originCode,
            destCode = template.destCode,
            departDate = date,
            departTime = departTime,
            arrivalTime = arrivalTime,
            arrivalOffsetDays = arrivalOffsetDays,
            durationMinutes = durationMinutes,
            stops = template.stops,
            legDepartureTimes = zoned.legDepartureTimes,
            legArrivalTimes = zoned.legArrivalTimes,
            legArrivalOffsetDays = zoned.legArrivalOffsetDays,
            legFlightNumbers = gaLegFlightNumbers(template, variantIdx),
            priceLight = light,
            priceEssential = essential.setScale(2, RoundingMode.HALF_UP),
            priceFlex = flex.setScale(2, RoundingMode.HALF_UP),
            recommendedRank = template.recommendedRankBase + dateBump + variantIdx,
            stopoverCodes = template.stopoverCodes,
            stopoverLayoverMinutes = layoverMinutesFromLegTimes(zoned.legArrivalTimes, zoned.legDepartureTimes),
        )
    }

    /**
     * After [diversifyItineraryTimes], each leg's block length (air / layover) is kept, but clock
     * times are recomputed by walking an `Instant` timeline so every segment endpoint is
     * shown in **that airport's** zone (true elapsed [durationMinutes] vs naive local subtraction).
     */
    private data class ZonedLegTimes(
        val legDepartureTimes: List<LocalTime>,
        val legArrivalTimes: List<LocalTime>,
        val legArrivalOffsetDays: List<Int>,
        val durationMinutes: Int,
        val arrivalOffsetDays: Int,
    )

    private fun applyAirportLocalZoneConversion(
        departDate: LocalDate,
        originCode: String,
        destCode: String,
        stopoverCodes: List<String>,
        legDeps: List<LocalTime>,
        legArrs: List<LocalTime>,
        stops: Int,
        templateArrivalOffsetHint: Int,
    ): ZonedLegTimes {
        val n = stops + 1
        val path =
            buildList {
                add(originCode)
                addAll(stopoverCodes)
                add(destCode)
            }
        val zones = path.map { AirportTimeZones.zoneIdForIata(it) }
        require(legDeps.size == n && legArrs.size == n) { "Expected $n legs, got deps=${legDeps.size} arrs=${legArrs.size}" }
        val air = (0 until n).map { segmentAirMinutes(legDeps[it], legArrs[it]) }
        val lays =
            if (stops == 0) {
                emptyList()
            } else {
                (0 until stops).map { segmentLayoverMinutes(legArrs[it], legDeps[it + 1]) }
            }

        var instant = ZonedDateTime.of(departDate, legDeps[0], zones[0]).toInstant()
        val tStart = instant

        val newDeps = mutableListOf<LocalTime>()
        val newArrs = mutableListOf<LocalTime>()
        val legArrivalDayOffsets = mutableListOf<Int>()

        newDeps.add(snapToFiveMinuteGrid(LocalTime.ofInstant(instant, zones[0])))

        for (i in 0 until n) {
            instant = instant.plus(air[i].toLong(), ChronoUnit.MINUTES)
            val arrZone = zones[i + 1]
            newArrs.add(snapToFiveMinuteGrid(LocalTime.ofInstant(instant, arrZone)))
            val arrLocalDate = instant.atZone(arrZone).toLocalDate()
            legArrivalDayOffsets.add(
                ChronoUnit.DAYS.between(departDate, arrLocalDate).toInt().coerceAtLeast(0),
            )
            if (i < stops) {
                instant = instant.plus(lays[i].toLong(), ChronoUnit.MINUTES)
                val hubZone = zones[i + 1]
                newDeps.add(snapToFiveMinuteGrid(LocalTime.ofInstant(instant, hubZone)))
            }
        }

        val durationMinutes = Duration.between(tStart, instant).toMinutes().toInt().coerceAtLeast(1)
        val destZone = zones.last()
        val finalArrivalDate = instant.atZone(destZone).toLocalDate()
        val arrivalOffsetDays =
            maxOf(
                templateArrivalOffsetHint,
                ChronoUnit.DAYS.between(departDate, finalArrivalDate).toInt().coerceAtLeast(0),
            )

        return ZonedLegTimes(
            legDepartureTimes = newDeps,
            legArrivalTimes = newArrs,
            legArrivalOffsetDays = legArrivalDayOffsets,
            durationMinutes = durationMinutes,
            arrivalOffsetDays = arrivalOffsetDays,
        )
    }

    /**
     * Per-variant air-time and layover tweaks so gate-to-gate minutes and displayed clocks differ
     * (avoids every option showing the same :55→:45 pattern); trades longer/shorter flying vs longer/shorter connections.
     */
    private fun diversifyItineraryTimes(
        dep: List<LocalTime>,
        arr: List<LocalTime>,
        stops: Int,
        variantIdx: Int,
        routeKey: String,
    ): Pair<List<LocalTime>, List<LocalTime>> {
        val n = stops + 1
        if (dep.size != n || arr.size != n || dep.isEmpty()) return dep to arr
        val mix = (routeKey.hashCode() xor (variantIdx * 1009)).absoluteValue
        if (stops == 0) {
            // Pin non-stop departures to morning / afternoon / night instead of all landing in 00:xx
            // after template shifts (variant anchor + day-part anchors stack into the red-eye band).
            val bandSlot = variantIdx % 3
            val origAir = segmentAirMinutes(dep[0], arr[0])
            val airDelta = ((variantIdx * 31 + mix) % 51) - 25
            val air = (origAir + airDelta).coerceIn(180, 960)
            val depMinOfDay =
                when (bandSlot) {
                    0 -> MORNING_START + 90 + (mix % 150)
                    1 -> MORNING_END + 45 + (mix % 150)
                    else -> AFTERNOON_END + 15 + (mix % 150)
                }
            val d0 = localTimeFromMinuteOfDay(depMinOfDay.coerceIn(0, 24 * 60 - 1))
            val a0 = snapToFiveMinuteGrid(d0.plusMinutes(air.toLong()))
            return listOf(d0) to listOf(a0)
        }
        val air = (0 until n).map { segmentAirMinutes(dep[it], arr[it]) }.toMutableList()
        val lays = (0 until stops).map { segmentLayoverMinutes(arr[it], dep[it + 1]) }.toMutableList()
        val dep0Nudge = (mix % 58).toLong()
        for (i in 0 until n) {
            val airDelta = ((variantIdx * (17 + i * 5) + mix / (i + 2)) % 51) - 25
            air[i] = (air[i] + airDelta).coerceAtLeast(25)
        }
        for (i in 0 until stops) {
            val layDelta = 15 + ((variantIdx * (23 + i * 7) + mix + i * 11) % 46)
            lays[i] = (lays[i] + layDelta).coerceAtLeast(45)
        }
        val newDep = MutableList(n) { dep[0] }
        val newArr = MutableList(n) { arr[0] }
        newDep[0] = snapToFiveMinuteGrid(dep[0].plusMinutes(dep0Nudge))
        for (i in 0 until n) {
            newArr[i] = snapToFiveMinuteGrid(newDep[i].plusMinutes(air[i].toLong()))
            if (i < stops) {
                newDep[i + 1] = snapToFiveMinuteGrid(newArr[i].plusMinutes(lays[i].toLong()))
            }
        }
        return newDep to newArr
    }

    private fun localTimeFromMinuteOfDay(minuteOfDay: Int): LocalTime {
        val m = ((minuteOfDay % (24 * 60)) + (24 * 60)) % (24 * 60)
        return snapToFiveMinuteGrid(LocalTime.of(m / 60, m % 60))
    }

    private fun segmentAirMinutes(dep: LocalTime, arr: LocalTime): Int {
        var d = toMinuteOfDay(arr) - toMinuteOfDay(dep)
        if (d <= 0) d += 24 * 60
        return d
    }

    private fun segmentLayoverMinutes(arrPrev: LocalTime, depNext: LocalTime): Int {
        var d = toMinuteOfDay(depNext) - toMinuteOfDay(arrPrev)
        if (d < 0) d += 24 * 60
        return d
    }

    /**
     * Keep at most [MAX_FLIGHTS_PER_ARRIVAL_HOUR_BUCKET] arrivals in the same local arrival-hour
     * bucket per origin/destination/day offset (enough for ≥5 choices per route when templates vary).
     */
    private fun enforceArrivalHourCap(rows: List<FlightScheduleRecord>): List<FlightScheduleRecord> {
        return rows
            .groupBy { r -> listOf(r.originCode, r.destCode, r.arrivalOffsetDays.toString(), r.arrivalTime.hour.toString()) }
            .values
            .flatMap { bucket ->
                bucket.sortedWith(recommendedComparator()).take(MAX_FLIGHTS_PER_ARRIVAL_HOUR_BUCKET)
            }
    }

    /**
     * Seeds morning/afternoon/night coverage **per stop class** (0 / 1 / 2), enforces “any 2-stop ⇒
     * include a 1-stop” when the pool allows, then pairwise spacing and minimum count.
     */
    private fun applyRoutePresentationRules(candidates: List<FlightScheduleRecord>): List<FlightScheduleRecord> {
        if (candidates.isEmpty()) return emptyList()
        val cmp = recommendedComparator()
        val seed = seedTimeOfDayPerStopClassAndHierarchy(candidates, cmp)
        val order = buildInterleavedProcessingOrder(seed, candidates, cmp)
        val kept = mutableListOf<FlightScheduleRecord>()
        for (f in order) {
            val trial = kept + f
            if (passesDeparturePairwiseSpacing(trial) && passesArrivalPairwiseSpacing(trial)) {
                kept.add(f)
            }
        }
        ensureMissingStopClassIfInPool(kept, candidates, cmp)
        ensureOneStopIfTwoStopPresent(kept, candidates, cmp)
        ensureDepartureDayPartBands(kept, candidates)
        topUpToMinimumCount(kept, candidates, minCount = 5)
        return kept.sortedWith(cmp)
    }

    /**
     * Process 0-stop / 1-stop / 2-stop in round-robin so greedy spacing does not eat all slots with
     * directs before any connecting itinerary is tried.
     */
    private fun buildInterleavedProcessingOrder(
        seed: List<FlightScheduleRecord>,
        candidates: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ): List<FlightScheduleRecord> {
        val q0 = mutableListOf<FlightScheduleRecord>()
        val q1 = mutableListOf<FlightScheduleRecord>()
        val q2 = mutableListOf<FlightScheduleRecord>()
        for (r in seed) {
            when (r.stops) {
                0 -> q0.add(r)
                1 -> q1.add(r)
                else -> q2.add(r)
            }
        }
        val seedSet = seed.toSet()
        for (r in candidates.sortedWith(cmp)) {
            if (r in seedSet) continue
            when (r.stops) {
                0 -> q0.add(r)
                1 -> q1.add(r)
                else -> q2.add(r)
            }
        }
        val order = mutableListOf<FlightScheduleRecord>()
        while (q0.isNotEmpty() || q1.isNotEmpty() || q2.isNotEmpty()) {
            if (q0.isNotEmpty()) order.add(q0.removeAt(0))
            if (q1.isNotEmpty()) order.add(q1.removeAt(0))
            if (q2.isNotEmpty()) order.add(q2.removeAt(0))
        }
        return order
    }

    /** If the pool has a stop class but greedy dropped it entirely, force the best option in (spacing permitting). */
    private fun ensureMissingStopClassIfInPool(
        kept: MutableList<FlightScheduleRecord>,
        pool: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ) {
        for (s in 0..2) {
            if (pool.none { it.stops == s }) continue
            if (kept.any { it.stops == s }) continue
            val add = pool.filter { it.stops == s }.minWithOrNull(cmp) ?: continue
            var trial = kept + add
            if (passesDeparturePairwiseSpacing(trial) && passesArrivalPairwiseSpacing(trial)) {
                kept.add(add)
                continue
            }
            for (dropStops in listOf(2, 0)) {
                while (kept.any { it.stops == dropStops }) {
                    val victim = kept.filter { it.stops == dropStops }.maxWithOrNull(cmp) ?: break
                    kept.remove(victim)
                    trial = kept + add
                    if (passesDeparturePairwiseSpacing(trial) && passesArrivalPairwiseSpacing(trial)) {
                        kept.add(add)
                        break
                    }
                }
                if (kept.any { it.stops == s }) break
            }
        }
    }

    /**
     * For each stop count (0 / 1 / 2), pick representative departures: up to one per morning,
     * afternoon, night when ≥3 options exist; 2 options → morning + afternoon when possible;
     * 1 option → prefer morning band.
     */
    private fun seedTimeOfDayPerStopClassAndHierarchy(
        candidates: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ): List<FlightScheduleRecord> {
        val out = linkedSetOf<FlightScheduleRecord>()
        for (stops in 0..2) {
            val g = candidates.filter { it.stops == stops }.sortedWith(cmp)
            when {
                g.isEmpty() -> {}
                g.size == 1 -> out.add(morningPreferred(g, cmp))
                g.size == 2 -> out.addAll(morningAfternoonPair(g, cmp))
                else -> out.addAll(upToThreeDayParts(g, cmp))
            }
        }
        if (out.any { it.stops == 2 } && candidates.any { it.stops == 1 } && out.none { it.stops == 1 }) {
            candidates.filter { it.stops == 1 }.minWithOrNull(cmp)?.let { out.add(it) }
        }
        return out.toList()
    }

    private fun morningPreferred(
        g: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ): FlightScheduleRecord {
        return g.filter { departureBand(it.departTime) == 0 }.minWithOrNull(cmp) ?: g.minWithOrNull(cmp)!!
    }

    private fun morningAfternoonPair(
        g: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ): List<FlightScheduleRecord> {
        val m = g.filter { departureBand(it.departTime) == 0 }.minWithOrNull(cmp)
        val a = g.filter { departureBand(it.departTime) == 1 }.minWithOrNull(cmp)
        return when {
            m != null && a != null -> listOf(m, a)
            m != null -> listOf(m, g.filter { it != m }.minWithOrNull(cmp)!!)
            a != null -> listOf(a, g.filter { it != a }.minWithOrNull(cmp)!!)
            else -> g.take(2)
        }
    }

    private fun upToThreeDayParts(
        g: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ): List<FlightScheduleRecord> {
        val m = g.filter { departureBand(it.departTime) == 0 }.minWithOrNull(cmp)
        val a = g.filter { departureBand(it.departTime) == 1 }.minWithOrNull(cmp)
        val n = g.filter { departureBand(it.departTime) == 2 }.minWithOrNull(cmp)
        val picked = listOfNotNull(m, a, n).distinct()
        if (picked.size >= 3) return picked.take(3)
        val need = 3 - picked.size
        val rest = g.filter { it !in picked }.sortedWith(cmp).take(need)
        return picked + rest
    }

    /** If the result set includes a 2-stop but the pool has 1-stop options, ensure one 1-stop is kept. */
    private fun ensureOneStopIfTwoStopPresent(
        kept: MutableList<FlightScheduleRecord>,
        pool: List<FlightScheduleRecord>,
        cmp: Comparator<FlightScheduleRecord>,
    ) {
        if (!kept.any { it.stops == 2 }) return
        if (kept.any { it.stops == 1 }) return
        val add = pool.filter { it.stops == 1 }.minWithOrNull(cmp) ?: return
        if (add in kept) return
        val trial = kept + add
        if (passesDeparturePairwiseSpacing(trial) && passesArrivalPairwiseSpacing(trial)) {
            kept.add(add)
            return
        }
        while (kept.size > 1) {
            val victim = kept.filter { it.stops == 2 }.maxWithOrNull(cmp) ?: break
            kept.remove(victim)
            val t2 = kept + add
            if (passesDeparturePairwiseSpacing(t2) && passesArrivalPairwiseSpacing(t2)) {
                kept.add(add)
                return
            }
        }
    }

    private fun ensureDepartureDayPartBands(
        kept: MutableList<FlightScheduleRecord>,
        pool: List<FlightScheduleRecord>,
    ) {
        for (band in 0..2) {
            if (kept.any { departureBand(it.departTime) == band }) continue
            val pick =
                pool
                    .filter { departureBand(it.departTime) == band }
                    .minWithOrNull(recommendedComparator())
                    ?: continue
            kept.add(pick)
            while ((!passesDeparturePairwiseSpacing(kept) || !passesArrivalPairwiseSpacing(kept)) && kept.size > 1) {
                val victim = kept.filter { it != pick }.maxWithOrNull(recommendedComparator()) ?: break
                kept.remove(victim)
            }
            if (!passesDeparturePairwiseSpacing(kept) || !passesArrivalPairwiseSpacing(kept)) {
                kept.remove(pick)
            }
        }
    }

    private fun topUpToMinimumCount(
        kept: MutableList<FlightScheduleRecord>,
        pool: List<FlightScheduleRecord>,
        minCount: Int,
    ) {
        val pref = pool.sortedWith(recommendedComparator())
        for (f in pref) {
            if (kept.size >= minCount) return
            if (f in kept) continue
            val trial = kept + f
            if (passesDeparturePairwiseSpacing(trial) && passesArrivalPairwiseSpacing(trial)) {
                kept.add(f)
            }
        }
        // Short EU hops can exhaust spacing rules before five options; fill so search never looks empty.
        for (f in pref) {
            if (kept.size >= minCount) return
            if (f !in kept) kept.add(f)
        }
    }

    /** 0 = morning (05:00–11:59), 1 = afternoon (12:00–17:59), 2 = night (else). */
    private fun departureBand(depart: LocalTime): Int {
        val m = toMinuteOfDay(depart)
        if (m < MORNING_START) return 2
        if (m < MORNING_END) return 0
        if (m < AFTERNOON_END) return 1
        return 2
    }

    private fun passesDeparturePairwiseSpacing(rows: List<FlightScheduleRecord>): Boolean {
        if (rows.size <= 1) return true
        val deps = rows.map { toMinuteOfDay(it.departTime) }
        for (i in deps.indices) {
            for (j in i + 1 until deps.size) {
                if (abs(deps[i] - deps[j]) < MIN_SPACING_MINUTES) return false
            }
        }
        return true
    }

    private fun passesArrivalPairwiseSpacing(rows: List<FlightScheduleRecord>): Boolean {
        if (rows.size <= 1) return true
        val arrs = rows.map { arrivalAbsoluteMinutes(it) }
        for (i in arrs.indices) {
            for (j in i + 1 until arrs.size) {
                if (abs(arrs[i] - arrs[j]) < MIN_SPACING_MINUTES) return false
            }
        }
        return true
    }

    private fun arrivalAbsoluteMinutes(r: FlightScheduleRecord): Int =
        r.arrivalOffsetDays * (24 * 60) + toMinuteOfDay(r.arrivalTime)

    /**
     * Deterministic “from” Light fare: varies by date + flight numbers + season-ish multiplier.
     */
    private fun generatedPriceLight(
        date: LocalDate,
        template: FlightTemplate,
        variantIdx: Int,
    ): BigDecimal {
        val hashInput = "${template.legFlightNumbers.joinToString("-")}-${date}-$variantIdx"
        val spread = (hashInput.hashCode().absoluteValue % 90) + 15
        val seasonMultiplier =
            when (date.monthValue) {
                6, 7, 8, 12 -> BigDecimal("1.14")
                else -> BigDecimal("1.00")
            }
        val base =
            BigDecimal(template.durationMinutes) * BigDecimal("0.39") +
                BigDecimal(template.stops * 58) +
                BigDecimal(spread) +
                BigDecimal("120")
        return (base * seasonMultiplier).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Layover minutes per stopover airport — stable for the same date + itinerary.
     */
    private fun layoverMinutesFromLegTimes(
        legArrivals: List<LocalTime>,
        legDepartures: List<LocalTime>,
    ): List<Int> {
        if (legArrivals.size <= 1 || legDepartures.size <= 1) return emptyList()
        return (0 until legArrivals.size - 1).map { idx ->
            val arr = legArrivals[idx]
            val nextDep = legDepartures[idx + 1]
            var diff = (nextDep.hour * 60 + nextDep.minute) - (arr.hour * 60 + arr.minute)
            while (diff <= 0) diff += 24 * 60
            diff
        }
    }

    private fun toMinuteOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

    private fun snapToFiveMinuteGrid(t: LocalTime): LocalTime {
        val total = t.hour * 60 + t.minute
        val rounded = (total / 5) * 5
        val hh = (rounded / 60) % 24
        val mm = rounded % 60
        return LocalTime.of(hh, mm)
    }

    /** GA + exactly three digits (staff CSV numbers are ignored for display consistency). */
    private fun gaLegFlightNumbers(template: FlightTemplate, flatVariantIdx: Int): List<String> {
        return template.legFlightNumbers.indices.map { legIdx ->
            val seed = "${template.originCode}|${template.destCode}|${template.legFlightNumbers[legIdx]}|$flatVariantIdx|$legIdx"
            val n = (seed.hashCode().absoluteValue % 900) + 100
            "GA$n"
        }
    }

    /**
     * Read [AIRPORTS_CSV]; if missing, write bundled defaults then parse.
     */
    private fun loadAirportsWithFallback(): List<AirportMeta> {
        ensureAirportsSeedFile()
        if (!AIRPORTS_CSV.exists()) return emptyList()
        return AIRPORTS_CSV
            .readLines()
            .drop(1)
            .mapNotNull { line -> parseAirportLine(line) }
    }

    /**
     * Read [TEMPLATES_CSV]; if missing, write bundled defaults then parse.
     */
    private fun loadTemplatesWithFallback(): List<FlightTemplate> {
        ensureTemplatesSeedFile()
        if (!TEMPLATES_CSV.exists()) return emptyList()
        return TEMPLATES_CSV
            .readLines()
            .drop(1)
            .mapNotNull { line -> parseTemplateLine(line) }
    }

    /**
     * Parse one non-header line of [AIRPORTS_CSV]: code,city,airportName,aliases
     */
    private fun parseAirportLine(line: String): AirportMeta? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 3) return null
        val aliases =
            parts.getOrNull(3)
                ?.takeIf { it.isNotBlank() }
                ?.split("|")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        return AirportMeta(
            code = parts[0].uppercase(),
            city = parts[1],
            airportName = parts[2],
            aliases = aliases,
        )
    }

    /**
     * Parse one non-header line of [TEMPLATES_CSV].
     *
     * Columns: originCode, destCode, durationMinutes, stops, stopoverCodes, recommendedRankBase,
     * arrivalOffsetDays, legDepartureTimes, legArrivalTimes, legFlightNumbers
     * (pipe-separated lists must have length stops + 1).
     */
    private fun parseTemplateLine(line: String): FlightTemplate? {
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 10) return null
        return try {
            val stops = parts[3].toInt()
            val stopRaw = parts.getOrNull(4).orEmpty()
            val stopoverCodes =
                if (stopRaw.isBlank()) {
                    emptyList()
                } else {
                    stopRaw.split("|").map { it.trim().uppercase() }.filter { it.isNotBlank() }
                }
            val legDeps =
                parts[7].split("|").map { LocalTime.parse(it.trim(), TIME_FMT) }
            val legArrs =
                parts[8].split("|").map { LocalTime.parse(it.trim(), TIME_FMT) }
            val legFns =
                parts[9].split("|").map { it.trim() }.filter { it.isNotBlank() }
            val expected = stops + 1
            if (legDeps.size != expected || legArrs.size != expected || legFns.size != expected) {
                return null
            }
            if (stopoverCodes.size != stops) return null

            FlightTemplate(
                originCode = parts[0].uppercase(),
                destCode = parts[1].uppercase(),
                durationMinutes = parts[2].toInt(),
                stops = stops,
                stopoverCodes = stopoverCodes,
                recommendedRankBase = parts[5].toInt(),
                arrivalOffsetDays = parts[6].toInt(),
                legDepartureTimes = legDeps,
                legArrivalTimes = legArrs,
                legFlightNumbers = legFns,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Create [AIRPORTS_CSV] with default content if the file is absent (same pattern as [AirportRepository]).
     */
    private fun ensureAirportsSeedFile() {
        AIRPORTS_CSV.parentFile?.mkdirs()
        if (AIRPORTS_CSV.exists()) return
        AIRPORTS_CSV.writeText(
            """
            code,city,airportName,aliases
            MAN,Manchester,Manchester Airport,manchester
            HKG,Hong Kong,Hong Kong International Airport,hong kong
            """.trimIndent() + "\n",
        )
    }

    /**
     * Create [TEMPLATES_CSV] with a minimal default if the file is absent.
     */
    private fun ensureTemplatesSeedFile() {
        TEMPLATES_CSV.parentFile?.mkdirs()
        if (TEMPLATES_CSV.exists()) return
        TEMPLATES_CSV.writeText(
            """
            originCode,destCode,durationMinutes,stops,stopoverCodes,recommendedRankBase,arrivalOffsetDays,legDepartureTimes,legArrivalTimes,legFlightNumbers
            MAN,HKG,785,1,DXB,10,1,11:10|14:40,20:15|06:35,GA218|GA319
            """.trimIndent() + "\n",
        )
    }
}
