# Flight schedule “database” — requirements checklist

This document lists **behavioural and data rules** implemented (or partially implemented) for the teaching project’s flight search. It is the single place to see what is **in scope** vs **not modelled** (e.g. real APIs, UTC, MCT from airlines).

---

## 1. Data sources (files)

| Requirement | Status | Notes |
|-------------|--------|--------|
| Airport list for the **homepage** (`data/airports.csv`) | Implemented | One display line per airport, `City (IATA)`. |
| Rich airport metadata for **search resolution** (`data/airports_display.csv`) | Implemented | IATA code, city, airport name, optional alias pipe-list for text search. |
| **Schedule templates** (`data/flight_schedule_templates.csv`) | Implemented | One row per *pattern* (not per calendar day): route, duration, stops, stopover codes, leg times, `legFlightNumbers`. Large file = many patterns per pair (§2.1). **Not** Kotlin-generated at runtime; library vs schedule, digit length, trade-offs: **§3.0** (and [`flights-handover.md`](flights-handover.md) §3.2a–§3.2d). |
| Regenerate templates from script | Implemented | `scripts/generate_flight_schedule_templates.py` — run after changing airport lists. |

---

## 2. Coverage & generation (CSV / script)

### 2.1 Why are there thousands of CSV rows?

Row count is **by design**, not “one dated timetable line per row”. For each ordered pair of airports in `CODES`, the Python generator adds **multiple template rows**: non-stop where the route rules allow, short EU directs plus connects, several **1-stop** variants (hub rotation + time jitter), and **two** **2-stop** hub-pair patterns. That produces roughly **order \(n^2\)** pairs × **many patterns each** → many thousands of lines, all still **templates** expanded at runtime for the chosen search date. A smaller file is only realistic if you relax coverage or merchandising diversity.

| Requirement | Status | Notes |
|-------------|--------|--------|
| Every ordered airport pair from the homepage list has **searchable** flights | Implemented | Generator emits rows for each `origin ≠ destination`. |
| **Non-stop** on long-haul / non–EU–EU pairs | Implemented | `row_direct_longhaul` in generator. |
| Short **EU–EU** non-stop | Implemented | `row_0stop_eu`. |
| **At least one 1-stop** pattern per route when **2-stop** exists | Implemented in data | Generator always adds multiple 1-stop rows; 2-stop rows are additional. |
| **Diverse hubs** for 1-stop (not only DXB) | Implemented | Hub pool in script (DXB, DOH, IST, SIN, …). |
| **2-stop** patterns with varied hub pairs | Implemented | `TWO_STOP_PATTERNS` rotated by hash. |
| **At most 5** results sharing the same `(stops, stopoverCodes)` for **connecting** flights | Implemented | `capIdenticalStopoverPatternConnecting`. |
| **Non-stop:** at most **one** flight per **departure day-part** (morning / afternoon / night) | Implemented | `capNonStopByDepartureDayPart` — avoids three red-eye clones. |
| **Non-stop times** pinned to civic bands | Implemented | `diversifyItineraryTimes` uses `variantIdx % 3` for morning / afternoon / evening local departure, not shifted-template red-eye. |
| **Greedy presentation order** interleaves 0 / 1 / 2 stops | Implemented | `buildInterleavedProcessingOrder` so 1-stop is not skipped after fills all directs. |
| **If pool has a stop class**, result should include it when possible | Implemented | `ensureMissingStopClassIfInPool` (may drop a worse 2-stop to fit). |

---

## 3. Runtime expansion (Kotlin)

### 3.0 Template library vs “real schedule”; who writes the CSV; flight number shape

- **`flight_schedule_templates.csv` is not** a dated operational timetable. It is a **pre-generated library** of candidate **travel patterns** for **all ordered airport pairs** in the generator’s `CODES` list — many rows per pair so Kotlin has a rich pool before caps/sort/page.
- **Kotlin does not generate this CSV at runtime.** The file is built **offline** by `scripts/generate_flight_schedule_templates.py`. **Kotlin only reads** it and expands rows for the searched route and date.
- **Why `GA9001`-style strings in the file?** The Python script uses numeric counters (e.g. `9000 + seq`) when it writes `legFlightNumbers`. That is **script bookkeeping**, not the UI format.
- **What users see:** `gaLegFlightNumbers` in `FlightScheduleRepository` always produces **`GA100`–`GA999`** (three digits). The CSV strings are **hash seeds**, not the literal displayed label (see also [`flights-handover.md`](flights-handover.md) §3.2a–§3.2c).

**Design trade-off — final numbers in CSV vs hash in Kotlin:**

| Final `GA###` in CSV | Current (CSV seeds → hash → `GA100`–`GA999`) |
|----------------------|---------------------------------------------|
| WYSIWYG for CSV editors. | One Kotlin function enforces a single display rule. |
| You maintain numbers per leg (and per variant if you want variants to differ without code). | Variant index is already in the seed; **no** extra CSV columns needed for variant-specific `GA###`. |
| Numbers stable if you only change Kotlin time/variant math and not CSV. | Changing seeds or hash logic can change digits; **no** app write-back to CSV. |

For the longer rationale, see [`flights-handover.md`](flights-handover.md) §3.2d.

| Requirement | Status | Notes |
|-------------|--------|--------|
| Expand **only the requested route** (performance) | Implemented | `expandRouteRecordsForDate(origin, dest)` — does not materialise the whole world. |
| **Day-part anchors** + **variant offsets** on each template | Implemented | Spreads departures across the day before diversification. |
| **Diversify** leg times / layovers per variant | Implemented | `diversifyItineraryTimes` — avoids identical duration and identical `:xx` patterns for every option. |
| **GA + three digits** flight numbers on every leg | Implemented | `gaLegFlightNumbers` — CSV `legFlightNumbers` strings are **not** the displayed label; they are part of a **deterministic hash seed** (with origin, destination, variant index, leg index). Kotlin maps to `GA100`–`GA999`. There is **no** write-back from the app into the CSV; changing a CSV value changes seed input, not “setting” the shown number by hand. |
| **Five-minute** clock grid on leg times | Implemented | `snapToFiveMinuteGrid`. |

---

## 4. Presentation rules (what the user sees per search)

| Requirement | Status | Notes |
|-------------|--------|--------|
| **Recommended** sort: stops → departure → arrival → Light fare → duration | Implemented | `recommendedComparator()`. |
| **Per stop class** (0 / 1 / 2 stops), prefer **time-of-day coverage** | Implemented | Seed picks: 1 flight → **morning** if possible; 2 → **morning + afternoon** when possible; 3+ → up to **morning, afternoon, night** (best in each band by comparator). |
| If any **2-stop** appears in the candidate pool, the result set should include a **1-stop** when one exists | Implemented | `seedTimeOfDayPerStopClassAndHierarchy` + `ensureOneStopIfTwoStopPresent` + interleaved greedy + `ensureMissingStopClassIfInPool`. |
| **Pairwise** departure/arrival spacing (≥ 60 min) | Best-effort | Greedy pass; hierarchy / min-count top-up can tighten spacing. |
| **At least 5** flights in the result list when the pool allows | Implemented | `topUpToMinimumCount` with relaxed fill for short EU hops if needed. |
| **Global** morning / afternoon / night band fill | Best-effort | `ensureDepartureDayPartBands` after greedy. |
| **Arrival-hour bucket** cap before presentation | Implemented | Reduces duplicate “same hour” clutter. |

---

## 5. UI / HTTP

| Requirement | Status | Notes |
|-------------|--------|--------|
| **10** flights per page on search results | Implemented | `FlightsRoutes` `pageSize = 10`. |

---

## 6. Automated tests (high level)

| Requirement | Status | Notes |
|-------------|--------|--------|
| GA### and five-minute times on a sample search | Tested | `FlightScheduleRepositoryTest`. |
| Every homepage pair → `totalCount ≥ 5` | Tested | Reads `data/airports.csv`. |
| MAN→HKG: ≥2 departure bands + GA### | Tested | Pairwise spacing **not** asserted (best-effort). |

---

## 7. Not implemented (out of scope for this codebase)

These items match **real** flight databases but are **not** part of this prototype:

- Live **Schedules APIs** (Aviationstack, Kiwi/Tequila, SITA, etc.).
- **OpenFlights** (or similar) ingestion.
- **Graph DB** (Neo4j) or **pathfinding** (Dijkstra/BFS) to **compose** connections from leg tables.
- **MCT** (minimum connection time) rules per airport/terminal.
- **UTC** storage and cross-timezone duration maths (times are **local** per leg for teaching).
- **Airline** table, equipment, codeshares, operational days of week.
- **Virtual interlining** across carriers.

---

## 8. Changing requirements later

1. **Data**: edit `airports.csv` / `airports_display.csv` / generator `CODES`, then run `python3 scripts/generate_flight_schedule_templates.py`.
2. **Logic caps**: constants at top of `FlightScheduleRepository.kt` (`MAX_FLIGHTS_PER_*`, `MIN_SPACING_MINUTES`, variant lists).
3. **Presentation**: `applyRoutePresentationRules` and helpers (`seedTimeOfDay…`, `ensureOneStopIfTwoStopPresent`, etc.).

Keep this file updated when you add or drop a rule.
