@file:Suppress(
    "InvalidPackageDeclaration",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "UnusedParameter",
)

package routes.flight

import io.ktor.http.Parameters
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

private const val LIGHT_JOURNEY_SEAT_FEE_GBP = 30
private const val MAX_ADULTS = 9
private const val MAX_CHILDREN = 8

private val PAY_TRAVEL_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

private fun formatPayDateRecord(date: LocalDate): String = date.format(PAY_TRAVEL_DATE_FORMAT)

private fun formatPayDateIso(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return ""
    return runCatching {
        LocalDate.parse(trimmed).format(PAY_TRAVEL_DATE_FORMAT)
    }.getOrElse { "" }
}

internal fun bookPaymentModel(queryParams: Parameters): Map<String, Any?> {
    val seatMap = decodeSeatSelection(queryParams["seatSel"])
    val paxNamesBySlot = decodePaxDisplayNames(queryParams["paxSel"])
    val isReturn = queryParams["trip"].equals("return", ignoreCase = true)
    val inboundRow = findRecordForBooking(queryParams)
    val outboundRow = findOutboundRecordForBooking(queryParams)

    val adults = queryParams["adults"]?.toIntOrNull()?.coerceIn(1, MAX_ADULTS) ?: 1
    val children = queryParams["children"]?.toIntOrNull()?.coerceIn(0, MAX_CHILDREN) ?: 0
    val paxCount = adults + children
    val paxMultiplier = BigDecimal.valueOf(paxCount.toLong())

    val legs =
        buildList {
            if (isReturn && outboundRow != null) {
                add(outboundRow.originCode to outboundRow.destCode)
            }
            if (inboundRow != null) {
                add(inboundRow.originCode to inboundRow.destCode)
            }
        }
    val cabinRaw =
        CabinNormalization.normalizedCabinForLegs(
            queryParams["cabinClass"].orEmpty(),
            legs,
        )

    val outboundTier =
        effectiveFareTier(
            if (isReturn) {
                queryParams["obFare"].orEmpty()
            } else {
                queryParams["fare"].orEmpty()
            },
        )
    val inboundTier = effectiveFareTier(queryParams["fare"].orEmpty())

    val outboundPerPerson: BigDecimal
    val inboundPerPerson: BigDecimal
    val outboundPackageName: String
    val inboundPackageName: String
    val departingCard: Map<String, Any?>?
    val returningCard: Map<String, Any?>?
    val departingRouteLine: String
    val returningRouteLine: String

    val dual = isReturn && outboundRow != null && inboundRow != null
    if (dual) {
        val obFares = cabinFareSet(outboundRow, cabinRaw)
        val ibFares = cabinFareSet(inboundRow, cabinRaw)
        outboundPerPerson = moneyForTier(obFares, outboundTier)
        inboundPerPerson = moneyForTier(ibFares, inboundTier)
        outboundPackageName = farePackageDisplayName(outboundTier, cabinRaw)
        inboundPackageName = farePackageDisplayName(inboundTier, cabinRaw)
        departingCard = flightCardMap(outboundRow, cabinRaw)
        returningCard = flightCardMap(inboundRow, cabinRaw)
        departingRouteLine =
            routeCityPairLine(
                queryParams["obFrom"].orEmpty(),
                queryParams["obTo"].orEmpty(),
            )
        returningRouteLine =
            routeCityPairLine(
                queryParams["from"].orEmpty(),
                queryParams["to"].orEmpty(),
            )
    } else if (inboundRow != null) {
        val ibFares = cabinFareSet(inboundRow, cabinRaw)
        outboundPerPerson = moneyForTier(ibFares, inboundTier)
        inboundPerPerson = BigDecimal.ZERO
        outboundPackageName = farePackageDisplayName(inboundTier, cabinRaw)
        inboundPackageName = ""
        departingCard = flightCardMap(inboundRow, cabinRaw)
        returningCard = null
        departingRouteLine =
            routeCityPairLine(
                queryParams["from"].orEmpty(),
                queryParams["to"].orEmpty(),
            )
        returningRouteLine = ""
    } else {
        outboundPerPerson =
            parseGbpAmount(
                if (isReturn) {
                    queryParams["outboundPrice"].orEmpty()
                } else {
                    queryParams["price"].orEmpty()
                },
            )
        inboundPerPerson =
            if (isReturn) {
                parseGbpAmount(queryParams["price"].orEmpty())
            } else {
                BigDecimal.ZERO
            }
        outboundPackageName =
            farePackageDisplayName(
                if (isReturn) outboundTier else inboundTier,
                cabinRaw,
            )
        inboundPackageName = if (isReturn) farePackageDisplayName(inboundTier, cabinRaw) else ""
        departingCard = null
        returningCard = null
        departingRouteLine =
            if (isReturn) {
                routeCityPairLine(
                    queryParams["obFrom"].orEmpty(),
                    queryParams["obTo"].orEmpty(),
                )
            } else {
                routeCityPairLine(
                    queryParams["from"].orEmpty(),
                    queryParams["to"].orEmpty(),
                )
            }
        returningRouteLine =
            if (isReturn) {
                routeCityPairLine(
                    queryParams["from"].orEmpty(),
                    queryParams["to"].orEmpty(),
                )
            } else {
                ""
            }
    }

    val outboundLineTotal = outboundPerPerson.multiply(paxMultiplier).setScale(2, RoundingMode.HALF_UP)
    val inboundLineTotal = inboundPerPerson.multiply(paxMultiplier).setScale(2, RoundingMode.HALF_UP)
    val step1FlightsSubtotal = outboundLineTotal.add(inboundLineTotal).setScale(2, RoundingMode.HALF_UP)
    val step1CombinedPerPerson = outboundPerPerson.add(inboundPerPerson).setScale(2, RoundingMode.HALF_UP)

    val step1BreakdownRows: List<Map<String, String>> =
        buildList {
            when {
                dual -> {
                    add(
                        mapOf(
                            "label" to "Outbound — $outboundPackageName",
                            "amountPlain" to formatGbpPlain(outboundPerPerson),
                        ),
                    )
                    add(
                        mapOf(
                            "label" to "Return — $inboundPackageName",
                            "amountPlain" to formatGbpPlain(inboundPerPerson),
                        ),
                    )
                }
                isReturn && inboundPerPerson.compareTo(BigDecimal.ZERO) != 0 -> {
                    add(
                        mapOf(
                            "label" to "Outbound — $outboundPackageName",
                            "amountPlain" to formatGbpPlain(outboundPerPerson),
                        ),
                    )
                    add(
                        mapOf(
                            "label" to "Return — $inboundPackageName",
                            "amountPlain" to formatGbpPlain(inboundPerPerson),
                        ),
                    )
                }
                outboundPackageName.isNotBlank() ->
                    add(
                        mapOf(
                            "label" to "Fare — $outboundPackageName",
                            "amountPlain" to formatGbpPlain(outboundPerPerson),
                        ),
                    )
                else ->
                    add(
                        mapOf(
                            "label" to "Flight fare (per person)",
                            "amountPlain" to formatGbpPlain(outboundPerPerson),
                        ),
                    )
            }
        }

    val outboundFareIsLight = outboundTier.equals("light", ignoreCase = true)
    val inboundFareIsLight = isReturn && inboundTier.equals("light", ignoreCase = true)

    val feeRows =
        buildList {
            add(
                seatFeeRow(
                    journeyLabel = if (isReturn) "Outbound journey" else "Selected journey",
                    fareTierRaw = outboundTier,
                    cabinRaw = cabinRaw,
                    hasSeatChoice = hasSeatsForJourney(seatMap, "outbound"),
                ),
            )
            if (isReturn) {
                add(
                    seatFeeRow(
                        journeyLabel = "Return journey",
                        fareTierRaw = inboundTier,
                        cabinRaw = cabinRaw,
                        hasSeatChoice = hasSeatsForJourney(seatMap, "inbound"),
                    ),
                )
            }
        }
    val totalSeatFee = feeRows.sumOf { row -> row["feeGbp"] as Int }
    val seatFeesAmount = BigDecimal.valueOf(totalSeatFee.toLong()).setScale(2, RoundingMode.HALF_UP)

    val passengerRows =
        buildPaymentPassengerRows(
            paxCount,
            paxNamesBySlot,
            seatMap,
            isReturn,
            outboundFareIsLight,
            inboundFareIsLight,
        )

    val grandTotal = step1FlightsSubtotal.add(seatFeesAmount).setScale(2, RoundingMode.HALF_UP)
    val perPassengerAllInPlain =
        formatGbpPlain(
            grandTotal.divide(paxMultiplier, 2, RoundingMode.HALF_UP),
        )

    val departingDateDisplay: String
    val returningDateDisplay: String
    if (dual) {
        departingDateDisplay = formatPayDateRecord(outboundRow.departDate)
        returningDateDisplay = formatPayDateRecord(inboundRow.departDate)
    } else if (inboundRow != null) {
        departingDateDisplay =
            formatPayDateRecord(inboundRow.departDate)
                .ifBlank { formatPayDateIso(queryParams["depart"]) }
        returningDateDisplay = ""
    } else {
        departingDateDisplay =
            if (isReturn) {
                formatPayDateIso(queryParams["obDepart"])
            } else {
                formatPayDateIso(queryParams["depart"])
            }
        returningDateDisplay =
            if (isReturn) {
                formatPayDateIso(queryParams["return"]).ifBlank { formatPayDateIso(queryParams["depart"]) }
            } else {
                ""
            }
    }

    return mapOf(
        "title" to "Confirm and pay",
        "chooseFlightsHref" to backToFlightSearchHref(queryParams),
        "passengersHref" to bookingHref("/book/passengers", queryParams),
        "seatsHref" to bookingHref("/book/seats", queryParams),
        "feeRows" to feeRows,
        "totalSeatFeeGbp" to totalSeatFee,
        "hasSeatSelection" to seatMap.isNotEmpty(),
        "grandTotalPlain" to formatGbpPlain(grandTotal),
        "perPassengerCombinedPlain" to perPassengerAllInPlain,
        "step1SubtotalPlain" to formatGbpPlain(step1FlightsSubtotal),
        "step1FlightsPerPersonSubtotalPlain" to formatGbpPlain(step1CombinedPerPerson),
        "step1BreakdownRows" to step1BreakdownRows,
        "step2ExtrasSubtotalPlain" to formatGbpPlain(seatFeesAmount),
        "paxCount" to paxCount,
        "dualLeg" to dual,
        "isReturn" to isReturn,
        "departingCard" to departingCard,
        "returningCard" to returningCard,
        "departingRouteLine" to departingRouteLine,
        "returningRouteLine" to returningRouteLine,
        "departingDateDisplay" to departingDateDisplay,
        "returningDateDisplay" to returningDateDisplay,
        "outboundPerPersonPlain" to formatGbpPlain(outboundPerPerson),
        "inboundPerPersonPlain" to formatGbpPlain(inboundPerPerson),
        "outboundLineTotalPlain" to formatGbpPlain(outboundLineTotal),
        "inboundLineTotalPlain" to formatGbpPlain(inboundLineTotal),
        "outboundPackageName" to outboundPackageName,
        "inboundPackageName" to inboundPackageName,
        "hasDepartingCard" to (departingCard != null),
        "hasReturningCard" to (returningCard != null),
        "passengerRows" to passengerRows,
    )
}

private fun buildPaymentPassengerRows(
    paxCount: Int,
    names: Map<Int, String>,
    seatMap: Map<String, Map<String, Map<String, String>>>,
    isReturn: Boolean,
    outboundLight: Boolean,
    inboundLight: Boolean,
): List<Map<String, Any?>> =
    List(paxCount) { idx ->
        val slot = idx + 1
        val nm = names[slot]?.trim().orEmpty().ifBlank { "Passenger $slot" }
        mapOf(
            "slot" to slot,
            "displayName" to nm,
            "outboundSeats" to
                seatSlotsLine(
                    legs = seatMap["outbound"],
                    slot = slot,
                    collapseLightAllUnselected = outboundLight,
                ),
            "returnSeats" to
                if (isReturn) {
                    seatSlotsLine(
                        legs = seatMap["inbound"],
                        slot = slot,
                        collapseLightAllUnselected = inboundLight,
                    )
                } else {
                    ""
                },
        )
    }

private fun seatSlotsLine(
    legs: Map<String, Map<String, String>>?,
    slot: Int,
    collapseLightAllUnselected: Boolean,
): String {
    if (legs == null) return "Not selected"
    val slotStr = slot.toString()
    val indices = legs.keys.mapNotNull { it.toIntOrNull() }.sorted()
    if (indices.isEmpty()) return "Not selected"
    val parts =
        indices.map { li ->
            legs[li.toString()]?.get(slotStr)?.takeIf { it.isNotBlank() } ?: "Not selected"
        }
    if (collapseLightAllUnselected && parts.all { it == "Not selected" }) {
        return "Not selected"
    }
    return parts.joinToString(" · ")
}

private fun parseGbpAmount(raw: String?): BigDecimal {
    val s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return BigDecimal.ZERO
    val normalized = s.replace(",", "").replace("£", "").trim()
    return normalized.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
}

private fun formatGbpPlain(amount: BigDecimal): String = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

private fun seatFeeRow(
    journeyLabel: String,
    fareTierRaw: String,
    cabinRaw: String,
    hasSeatChoice: Boolean,
): Map<String, Any?> {
    val tier = fareTierRaw.lowercase(Locale.UK).trim()
    val isLight = tier == "light"
    val fee = if (isLight && hasSeatChoice) LIGHT_JOURNEY_SEAT_FEE_GBP else 0
    return mapOf(
        "journeyLabel" to journeyLabel,
        "feeGbp" to fee,
    )
}

private fun hasSeatsForJourney(
    seatMap: Map<String, Map<String, Map<String, String>>>,
    journeyKey: String,
): Boolean = seatMap[journeyKey]?.values?.any { paxMap -> paxMap.isNotEmpty() } == true

private fun decodeSeatSelection(raw: String?): Map<String, Map<String, Map<String, String>>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val normalized = raw.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val json =
        runCatching {
            String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
        }.getOrNull() ?: return emptyMap()
    return parseSeatJson(json)
}

private fun parseSeatJson(json: String): Map<String, Map<String, Map<String, String>>> {
    val out = LinkedHashMap<String, Map<String, Map<String, String>>>()
    extractInnerObject(json, "outbound")?.let { out["outbound"] = parseJourneyLegs(it) }
    extractInnerObject(json, "inbound")?.let { out["inbound"] = parseJourneyLegs(it) }
    return out
}

private fun extractInnerObject(
    json: String,
    key: String,
): String? {
    val needle = "\"$key\""
    var from = 0
    while (from < json.length) {
        val i = json.indexOf(needle, from)
        if (i < 0) return null
        var j = i + needle.length
        while (j < json.length && json[j].isWhitespace()) j++
        if (j < json.length && json[j] == ':') {
            j++
            while (j < json.length && json[j].isWhitespace()) j++
            if (j >= json.length || json[j] != '{') return null
            val end = endOfMatchingBrace(json, j)
            return json.substring(j, end + 1)
        }
        from = i + needle.length
    }
    return null
}

private fun parseJourneyLegs(wrapped: String): Map<String, Map<String, String>> {
    val legs = LinkedHashMap<String, Map<String, String>>()
    val body = wrapped.trim()
    if (body.length < 2 || body.first() != '{' || body.last() != '}') return legs
    var i = 1
    val endBound = body.length - 1
    while (i < endBound) {
        while (i < endBound && (body[i].isWhitespace() || body[i] == ',')) i++
        if (i >= endBound) break
        if (body[i] != '"') {
            i++
            continue
        }
        val keyStart = i + 1
        val keyEnd = body.indexOf('"', keyStart)
        if (keyEnd < 0) break
        val legKey = body.substring(keyStart, keyEnd)
        i = keyEnd + 1
        while (i < endBound && body[i].isWhitespace()) i++
        if (i >= endBound || body[i] != ':') break
        i++
        while (i < endBound && body[i].isWhitespace()) i++
        if (i >= endBound || body[i] != '{') break
        val objEnd = endOfMatchingBrace(body, i)
        val paxObj = body.substring(i, objEnd + 1)
        if (legKey.all { it.isDigit() }) {
            legs[legKey] = parsePaxMap(paxObj)
        }
        i = objEnd + 1
    }
    return legs
}

private fun parsePaxMap(obj: String): Map<String, String> {
    val pax = LinkedHashMap<String, String>()
    val paxRegex = Regex("\"(\\d+)\"\\s*:\\s*\"([^\"]*)\"")
    for (p in paxRegex.findAll(obj)) {
        pax[p.groupValues[1]] = p.groupValues[2]
    }
    return pax
}

private fun endOfMatchingBrace(
    s: String,
    openIdx: Int,
): Int {
    require(s[openIdx] == '{')
    var depth = 0
    var i = openIdx
    while (i < s.length) {
        when (s[i]) {
            '"' -> i = skipJsonString(s, i)
            '{' -> {
                depth++
                i++
            }
            '}' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            else -> i++
        }
    }
    return s.lastIndex.coerceAtLeast(openIdx)
}

private fun skipJsonString(
    s: String,
    quoteIdx: Int,
): Int {
    var j = quoteIdx + 1
    while (j < s.length) {
        when (s[j]) {
            '\\' -> j += 2
            '"' -> return j + 1
            else -> j++
        }
    }
    return j
}

/** Matches `{ "slot": n, "displayName": "…" }` as produced by passenger step (`flights-results.js`). */
private val paxSlotThenDisplayName =
    Regex(""""slot"\s*:\s*(\d+)\s*,\s*"displayName"\s*:\s*"((?:[^"\\]|\\.)*)"""")

/** Same object with keys in the opposite order (still valid JSON). */
private val paxDisplayNameThenSlot =
    Regex(""""displayName"\s*:\s*"((?:[^"\\]|\\.)*)"\s*,\s*"slot"\s*:\s*(\d+)"""")

private fun decodePaxDisplayNames(raw: String?): Map<Int, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    val normalized = raw.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val json =
        runCatching {
            String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
        }.getOrNull() ?: return emptyMap()
    val out = linkedMapOf<Int, String>()
    for (match in paxSlotThenDisplayName.findAll(json)) {
        val slot = match.groupValues[1].toIntOrNull() ?: continue
        out[slot] = unescapeJsonString(match.groupValues[2])
    }
    for (match in paxDisplayNameThenSlot.findAll(json)) {
        val slot = match.groupValues[2].toIntOrNull() ?: continue
        out.putIfAbsent(slot, unescapeJsonString(match.groupValues[1]))
    }
    return out
}

private fun unescapeJsonString(s: String): String =
    buildString(s.length) {
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '"' -> {
                        append('"')
                        i += 2
                        continue
                    }
                    '\\' -> {
                        append('\\')
                        i += 2
                        continue
                    }
                    'n' -> {
                        append('\n')
                        i += 2
                        continue
                    }
                    'r' -> {
                        append('\r')
                        i += 2
                        continue
                    }
                    't' -> {
                        append('\t')
                        i += 2
                        continue
                    }
                }
            }
            append(s[i])
            i++
        }
    }
