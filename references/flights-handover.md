# Flights page handover — Glide Airways (`/search-flights` + `/book/passengers`)

This handover explains:
- what the user sees,
- how the page is built,
- where the data comes from,
- and where the main Kotlin logic lives.

**Maintenance:** When you change flight search routing, query preservation, `flights-results.js` navigation, or Pebble `data-*` attributes, **update this file** so the behaviour and invariants below stay accurate.

### CSS quick map (where to edit)

- [`flights-results.css`](../src/main/resources/static/css/flights-results.css): Step 1 flights aggregator used by search/review pages; imports `flights/core.css` and `flights/review.css`.
- [`static/css/flights/core.css`](../src/main/resources/static/css/flights/core.css): shared Step 1 flights UI (page shell, stepper, hero, date carousel, sort bar, search cards, route details, fare grid, pager, inbound/outbound recap).
- [`static/css/flights/review.css`](../src/main/resources/static/css/flights/review.css): Step 1 summary/review styling (`/book/review`) including leg cards, package summary, “select another …” controls, and continue CTA.
- [`passenger-info.css`](../src/main/resources/static/css/passenger-info.css): Step 2 passenger stylesheet entrypoint used by `/book/passengers` (imports passenger-specific rules only).
- [`static/css/flights/passengers.css`](../src/main/resources/static/css/flights/passengers.css): the passenger-specific rule set consumed by `passenger-info.css`.

### File ownership (easy to understand cheat sheet)

- **`flights-results.css`**: only a loader file. It bundles **Step 1** flight pages (`/search-flights`, `/book/review`). It is **not** where Step 2 form styles are authored.
- **`flights/core.css`**: shared flight-search visuals for Step 1 (cards, route details, fares, pager, top stepper shell). If a change affects departing/returning list cards, edit here first.
- **`flights/review.css`**: Step 1 review/summary page styling only (`/book/review`), including package panel and “select another …” controls.
- **`passenger-info.css`**: Step 2 entrypoint for `/book/passengers`. Keeps passenger styling physically separate from the Step 1 bundle.
- **`flights/passengers.css`**: actual Step 2 rule set (sign-in block, passenger/contact form, step-2 spacing). It is loaded through `passenger-info.css`.
- **`flights-results.js`**: booking flow behavior shared across Step 1/2 pages (fare selection navigation, route details animation, title dropdown helpers). Not a layout file.

Quick selector hint:
- `bp-wf-*` -> Step 2 passenger UI
- `review-wf-*` -> Step 1 review/summary UI
- `flight-card*`, `route-*`, `fare-*` -> Step 1 flight results/review cards

---

## 1) Flights flow tutorial (user-facing)

### 1.1 Choose flights page (`/search-flights`)
- Booking stepper shows a 4-step journey (Choose flights, Passenger info, Seat and extras, Confirm and pay).
- Route header (`Origin → Destination`) with **inbound leg** labelling when `leg=inbound` (return trip, second leg).
- Optional **“Back to departing flights”** when viewing inbound results, plus outbound recap line.
- Date carousel (7-day strip + previous/next week controls).
- Sort controls (`recommended`, `departure`, `arrival`, `duration`, `fare`, `stops`).
- Flight cards: summary row, **timeline**, **Route details** disclosure, fare chooser.
- Expandable fare panel and animated fare selection (JS).
- Paging for multi-page result sets.

### 1.2 Passenger info page (`/book/passengers`)
- Stepper shows Step 2 active.
- Step 1 **“Choose flights”** returns to search with preserved query (Plan B: **inbound** list if the booking was the return leg; includes `leg=inbound` when applicable).
- Summary shows route, dates, fare, flight id.
- Lead passenger form scaffold (demo).
- Template file now follows folder-per-page convention: `templates/flights/book-passengers/index.peb`.

### 1.3 Error checks and safety rules
- Search runs only when `from`, `to`, and `depart` can be parsed correctly.
- Invalid or missing query values show a safe empty state (no server crash).
- Sort and page inputs are sanitized (unknown sort becomes `recommended`; page is clamped).
- Arrival **+1 / +2** day badges from computed offsets.
- Cabin fare invariants on the **display** layer (`FlightsRoutes`), separate from CSV templates.

---

## 2) Route details — how it works

This is the expandable **Route details** area under each flight card.  
Think of it in three layers: **Kotlin data -> Pebble HTML -> CSS/JS behavior**.

### 2.1 Method: Kotlin builds blocks, template renders rows

**Idea:** We do not hand-write HTML for each route.  
Each [`FlightScheduleRecord`](../src/main/kotlin/data/FlightScheduleRepository.kt) already contains per-leg times and flight numbers.  
`FlightsRoutes` converts this into a simple list of blocks, and Pebble renders each block by `kind`.

`buildRouteBlocks(row)` in [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt) loops through each leg (the white border and layover grey border when user expands the route details):

1. **Segment block** (`kind: "segment"`): one block per flight leg with from/to airport, depart/arrive times, `+1/+2` day values, and flight number.
2. **Connection block** (`kind: "connect"`): inserted between legs, with stopover airport and layover text.

Airport names come from `FlightScheduleRepository.airportNameForCode` (CSV-backed, airports.csv).  
Times shown are local time at each airport.  
Timezone conversion is handled by [`applyAirportLocalZoneConversion`](../src/main/kotlin/data/FlightScheduleRepository.kt) using [`AirportTimeZones.kt`](../src/main/kotlin/data/AirportTimeZones.kt), so durations and `+1/+2` day badges stay consistent.

**Why this approach?** The template stays simple (just a loop and two block types), and works for direct, 1-stop, or multi-stop routes.

### 2.2 HTML structure (Pebble)

File: [`templates/flights/search-results.peb`](../src/main/resources/templates/flights/search-results.peb).

The fare section uses cabin flags (`isEconomyCabin`, `isBusinessCabin`, `isFirstCabin`) to control which columns appear. The Flex card now also branches its bullet text with `isFirstCabin`, so **Business Flex** and **First Flex** can carry different terms without duplicating the whole card structure. Current examples in the template include different cabin-baggage allowances and different change/cancellation fee values between Business Flex and First Flex.

- Native **`<details>` / `<summary>`** pattern:
  - `<details class="flight-card__details" data-route-details>` wraps the expandable region.
  - Only the **summary** text (“Route details”) toggles open/closed (narrow hit target; see CSS).
- Inner structure:
  - `.flight-card__details-body` — animation target for open/close.
  - `.route-itinerary.route-itinerary--ledger` — **ledger layout**: a two-column grid where the **left column** holds flight numbers (or join arrows) and the **right column** holds the segment or connection content.
  - Per block:
    - **Segment:** `.route-itinerary__row--segment` → `.route-seg` with times row, airport line, meta line.
    - **Connect:** `.route-itinerary__row--join` → `.route-connect` with “Connect at …”, airport name, layover duration.
  - **Footer:** `.route-total-bar` repeats **total duration** (same as the card headline duration), visually closing the panel.

**Why `<details>`?** Progressive enhancement: without JS, expand/collapse still works. JS only adds the **close animation** (below).

### 2.3 CSS structure

Files: [`static/css/flights-results.css`](../src/main/resources/static/css/flights-results.css) as the Step 1 aggregator, with modular files in [`static/css/flights/`](../src/main/resources/static/css/flights/) (`core.css`, `review.css`). Passenger-page styles are loaded separately via [`passenger-info.css`](../src/main/resources/static/css/passenger-info.css). Route-details selectors live in `core.css` (search for `.route-itinerary`, `.flight-card__details`).

- **Ledger layout:** Left column for flight numbers, right column for segment text.
- **Footer bar:** Keeps the flight-number chain and the Route details toggle aligned cleanly.
- **Open animation:** `flight-route-details-open` keyframes on `.flight-card__details-body` when `[open]` and not closing.
- **Close animation:** Clicking summary to close is intercepted by JS (see below); class `flight-card__details--closing` runs `flight-route-details-close` before removing `[open]`.

### 2.4 JS behavior: close animation + bfcache reset

File: [`static/js/flights-results.js`](../src/main/resources/static/js/flights-results.js).

- **Close animation:** Adds a short fade/lift when closing Route details (unless reduced motion is enabled).
- **`pageshow` reset:** Clears fare animation classes when users come back with browser back/forward cache.

**Why not JS for open?** Opening uses CSS only; closing needed custom timing to sync with `animationend`.

### 2.5 Card header timeline (related but separate)

Above Route details, the **horizontal timeline** (plane icon, track, stop dots) uses `card.timelineStops` (stopover codes only) and `card.stops` for BEM modifiers like `flight-card__timeline--stops-1`. That is **visual summary** only; full segment text lives in Route details.

---

## 3) “Database”, CSVs, and generated numbers (schedule + prices)

There is **no SQL database** in this project for flights. **Staff-editable CSV files** on disk are loaded once (lazy), then **expanded in memory** per search date.

**Mental model:** `flight_schedule_templates.csv` is **not** a list of actual dated scheduled flights. It is a **pre-generated library** of candidate **travel patterns** for airport combinations. **Kotlin does not create this file at app startup** — see **§3.2b**.

### 3.1 Files and roles

| File | Role |
|------|------|
| `data/airports_display.csv` | Airport **codes**, city labels, full names, **aliases** for `resolveAirportCode` (homepage + search). |
| `data/airports_geo.csv` | Lat/lon per IATA for [`GeoRepository`](../src/main/kotlin/data/GeoRepository.kt); required for homepage **latest offers** pricing-by-distance (see [`homepage-handover.md`](homepage-handover.md) §3.1). |
| `data/flight_schedule_templates.csv` | One row per **route pattern** (not per calendar day): duration, stops, stopover list, rank, **per-leg** local times and `legFlightNumbers` (pipe-separated). See **§3.2a–§3.2d** (authorship, seeds vs display, digit length, design trade-offs). |
| `AirportTimeZones.kt` | IATA → `ZoneId` for schedule display times and elapsed duration (not for offers). |

If a file is missing, `FlightScheduleRepository` **seeds a minimal default** (same pattern as other repositories) so the app still runs in empty workspaces.

### 3.2 Why templates + Kotlin expansion?

Storing every route for every day would make very large CSV files.  
Instead:

1. **Templates** encode the **shape** of a flight (segments, times, numbers).
2. **`buildRecordsForDate(date)`** expands **every** template into concrete rows for that calendar day.

**Why is `flight_schedule_templates.csv` so large (~9k+ rows)?** It is **not** one row per day. The generator (`scripts/generate_flight_schedule_templates.py`) walks **every ordered airport pair** (`origin ≠ destination` from the fixed `CODES` list) and emits **several patterns per pair**: at least one non-stop where allowed, EU short-haul directs plus connects, multiple **1-stop** rows per hub (time jitter so options are not identical), and **two** **2-stop** hub-pair variants. That gives Kotlin a **wide candidate pool** before presentation caps, sorting, and pagination thin the list. Re-run the script after changing airport lists; row count scales roughly with \(n(n-1)\) times patterns-per-pair.

### 3.2a CSV `legFlightNumbers` vs what the user sees (`GA###`)

- The UI always shows **`GA` plus three digits** per leg, produced in Kotlin by `gaLegFlightNumbers` in [`FlightScheduleRepository.kt`](../src/main/kotlin/data/FlightScheduleRepository.kt).
- Values in the CSV column are **not** rendered as the customer-facing flight number. They are included in a **deterministic hash seed** (together with origin, destination, variant index, and leg index) so different template legs tend to get **different** `GA###` codes.
- The app **does not** write generated numbers back into the CSV. Editing the CSV does not “set” the displayed number directly; it changes **inputs to the generator**, which may change the derived `GA###` unless you change the seed formula in code.
- The generator already emits strings like `GA9001|GA9002` for convenience; Kotlin still **re-hashes** them into the final `GA100`–`GA999` style. Treat the column as **stable per-leg identifiers / seed material**, not as the literal label shown in search results.

### 3.2b Who builds the template CSV (Python, not Kotlin)

- The large `flight_schedule_templates.csv` file is produced **offline** by [`scripts/generate_flight_schedule_templates.py`](../scripts/generate_flight_schedule_templates.py) (e.g. after changing airport lists). **Kotlin only reads** the file and expands matching templates in memory for the search date and route.
- Do **not** assume the JVM app regenerates the CSV by itself; re-run the script when you intentionally refresh template data.

### 3.2c Why the CSV sometimes shows a four-digit numeric part (`GA9001`) while the UI always shows three (`GA123`)

- The **Python** generator labels rows with counters such as `9000 + seq`, `9100 + seq`, etc., so strings like **`GA9001`** appear in the file. That is **generator bookkeeping**, not the enforced customer-facing format.
- **`gaLegFlightNumbers`** always maps to **`GA100`–`GA999`** (`hashCode` seed → `% 900 + 100`). The KDoc on that function in `FlightScheduleRepository.kt` states that staff CSV values are **ignored for display consistency** in the sense that the **final** label is always `GA` + exactly three digits — the CSV string still **feeds the hash** as seed input (see §3.2a).

### 3.2d Final display numbers in the CSV vs the current hash-based approach

You *could* store the exact `GA###` to show in the CSV instead of re-deriving them in Kotlin. Trade-offs:

| Putting final `GA###` in CSV | Current approach (CSV strings → hash → `GA100`–`GA999`) |
|------------------------------|--------------------------------------------------------|
| **WYSIWYG** for anyone editing the CSV: what you type is what passengers see. | **Single place** (`gaLegFlightNumbers`) enforces **one** display rule (always three digits after `GA`). |
| You must assign numbers for **every leg** of **every** template row and keep them distinct where marketing cares about uniqueness. | The **same** template row still yields **different** numbers per **variant** (`flatVariantIdx` is in the seed) **without** extra CSV columns per variant. |
| Changing variant/time logic in Kotlin does not by itself reshuffle flight numbers — unless you regenerate or hand-edit CSV. | Numbers stay **deterministic** from code + seeds; changing seed fields or the hash formula **can** change displayed digits (good for demos/tests to know). |

**If you only want visual consistency** (always three digits after `GA`), **Kotlin already does that.** Rewriting the Python script to emit shorter opaque seeds (e.g. `L1`, `L2`) would be **cosmetic** for file readability and would **change** the derived `GA###` values unless you deliberately keep equivalent seed strings or adjust the hash formula.

### 3.3 Variants: more flights without more CSV rows

`VARIANT_DEPARTURE_OFFSETS_MINUTES = [0, 30, 90]` — each template produces **three** schedule rows per day by shifting all leg departure/arrival times by that many minutes, then **snapping to a 5-minute grid** (`snapToFiveMinuteGrid`). Flight numbers are adjusted per variant (`variantFlightNumbers`) so variants do not look identical.

**Why:** Richer-looking results and sort/pagination demos without tripling the CSV.

### 3.4 Deterministic pricing (stable pseudo-random spread)

**Light** fare is computed in `generatedPriceLight`:

- **Not** runtime random. Same input always gives same output (good for testing and demos).
- **`spread`:** a hash-based value from flight numbers, date, and variant index. It gives variation, but stays deterministic.
- **Base formula:** duration + stops + spread + base value, then a seasonal multiplier.
- **Essential / Flex:** fixed increases from Light; Business/First display rules are applied later in `FlightsRoutes.cabinFareSet`.

**Why two stages?** CSV templates stay cabin-agnostic; **merchandising rules** live in one Kotlin place so you can change branding without re-exporting CSVs.

### 3.5 Layovers, time zones, and day offsets

- **`layoverMinutesFromLegTimes`:** For each stop, difference between next departure and previous arrival (each hub’s **local** clock after zone conversion), adding 24h if needed — **deterministic** from times, not random.
- **Per-leg `+1` / final arrival offset:** Computed from **calendar dates** in each segment’s arrival zone vs the search `departDate` (`ChronoUnit.DAYS`), not from naive minute rollover alone — drives `+1` on segment rows in Route details when the instant chain crosses local midnight.
- **`enforceArrivalHourCap`:** After expansion, caps how many flights land in the same **arrival hour bucket** per route/day offset (merchandising realism).

### 3.6 Tests that lock these behaviours in

- [`FlightScheduleRepositoryTest.kt`](../src/test/kotlin/data/FlightScheduleRepositoryTest.kt) — e.g. GA number format, 5-minute grid.
- [`SearchFlightsIntegrationTest.kt`](../src/test/kotlin/routes/SearchFlightsIntegrationTest.kt) — cabin display invariants and booking navigation links.

---

## 4) End-to-end request lifecycle

### 4.1 `/search-flights`
1. Parse query (`from`, `to`, `depart`, `trip`, `leg`, `obFrom` / `obTo` / `obDepart`, `cabinClass`, …).
2. Resolve airports to IATA codes.
3. `FlightScheduleRepository.search(...)`.
4. Build view model: cards (including `routeBlocks`), carousel, sort links, pager, inbound/outbound helpers.
5. Render `templates/flights/search-results.peb`.

### 4.2 `/book/passengers`
1. Read query (including `leg`, `ob*`, prices).
2. Build `backToChooseFlightsHref` (inbound vs outbound context) and optional `backToOutboundFlightsHref`.
3. Render `templates/flights/book-passengers/index.peb`.

**Why querystrings?** Shareable, bookmarkable state; SSR stays stateless.

### 4.3 Return-trip date on inbound leg (important)

After the user picks an outbound fare on a return trip, the app moves to inbound search (`leg=inbound`).  
At that point, the inbound date is carried in `depart` and `return` may be empty.  
When building **Back to departing flights**, `buildOutboundLegSearchParams` must restore `return`; otherwise `data-search-return` is missing and fare selection can navigate to the wrong page.

The template exposes `returnDateForFareNav` so inbound cards still have `data-search-return` even when raw `return` is blank.

## 5) Code map (quick reference)

| Area | File | Purpose |
|------|------|---------|
| HTTP routes, card view-model, `buildRouteBlocks`, cabin fares | [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt) | Everything the Pebble template needs as plain maps/lists. |
| CSV load, expand templates, pricing/layover math | [`FlightScheduleRepository.kt`](../src/main/kotlin/data/FlightScheduleRepository.kt) | Single source of **schedule** truth. |
| Markup | [`search-results.peb`](../src/main/resources/templates/flights/search-results.peb), [`book-passengers/index.peb`](../src/main/resources/templates/flights/book-passengers/index.peb), [`book-review/index.peb`](../src/main/resources/templates/flights/book-review/index.peb) | Structure and accessibility. |
| Interactivity | [`flights-results.js`](../src/main/resources/static/js/flights-results.js) | Fare panels, selection animation, navigation to passengers / inbound search, route-details close. |
| Layout / motion | [`flights-results.css`](../src/main/resources/static/css/flights-results.css), [`passenger-info.css`](../src/main/resources/static/css/passenger-info.css), [`static/css/flights/`](../src/main/resources/static/css/flights/) | Step 1 flights bundle (`core.css` + `review.css`) plus separate Step 2 passenger entrypoint (`passenger-info.css` -> `passengers.css`). |

### 5.1 Kotlin operations map

Kotlin logic is required and is a core part of this flow.

- `handleSearchFlightsList` in [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt)
  - parses and cleans query params (`sort`, `order`, `page`, `trip`, `leg`, `ob*`)
  - resolves airports via repository helpers
  - calls `FlightScheduleRepository.search(...)`
  - builds SSR model (`flightCards`, `carouselDays`, `sortLinks`, pager, inbound/outbound helpers)
- `flightCardMap` in [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt)
  - maps each `FlightScheduleRecord` into template fields and `data-*` attributes
  - applies cabin fare view rules (`cabinFareSet`, `enforceFareInvariants`)
- `buildRouteBlocks` in [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt)
  - converts per-leg schedule data into `segment` / `connect` rows for Route details
  - computes `depPlusDays` / `arrPlusDays` for multi-stop timelines
- `handleBookReview` and `handleBookPassengers` in [`FlightsRoutes.kt`](../src/main/kotlin/routes/FlightsRoutes.kt)
  - preserves outbound/inbound context and selected fare in query params
  - build "Select another ..." and "Continue" navigation URLs
- `search` + template expansion in [`FlightScheduleRepository.kt`](../src/main/kotlin/data/FlightScheduleRepository.kt)
  - expands `flight_schedule_templates.csv` for the selected date
  - sorts/pages/thins results
  - computes layovers, duration, arrival day offsets, deterministic base fares
- `applyAirportLocalZoneConversion` in [`FlightScheduleRepository.kt`](../src/main/kotlin/data/FlightScheduleRepository.kt)
  - performs timezone-aware timeline conversion using [`AirportTimeZones.kt`](../src/main/kotlin/data/AirportTimeZones.kt)
  - produces per-leg cumulative day offsets used by route details and card badges

---

## 6) Front-end behaviour summary

- **Fare panels:** JS toggles `data-state` and `aria-expanded` / `inert` on the clip panel; only one card open at a time (optional UX choice).
- **Fare selection:** Configurable animation plan (`FARE_SELECT_PLAN`); then navigates to `/search-flights` (return → inbound leg) or `/book/passengers`.
- **Passenger form validation (client-side only):** `flights-results.js` enforces title/name/contact checks and shows inline red error boxes under the relevant passenger/contact white cards.

---

## 7) Passenger data lifecycle (current state)

This section clarifies where user-entered passenger/contact values are currently stored.

### 7.1 What is persisted today
- **Selected flight + fare context** is carried in the URL query string between pages (`from`, `to`, `depart`, `trip`, `fare`, `flight`, `price`, `leg`, `ob*`, etc.).
- **Login session identity** (when logged in) is session-backed and used to derive a display membership id in Kotlin (`GA` + padded numeric id).

### 7.2 What is not persisted yet (important)
- Passenger form inputs on `/book/passengers` are **not posted** to a backend endpoint yet.
- The page currently uses `form action="#"` and the Continue control is an anchor (`<a href="{{ continueSeatsHref }}">`), so values are validated in JS but not saved server-side.
- Result: typed passenger/contact/membership values do not survive a hard refresh or navigation away/back unless the browser keeps the page state temporarily.

### 7.3 Membership number behaviour
- Logged out: template renders `Membership Number` as readonly and prefilled `N/A`.
- Logged in: Kotlin provides `membershipValue` from session id and template pre-fills that value.

### 7.4 Next unfinished parsing/integration work
1. Add a real POST endpoint (or session-backed save endpoint) for passenger/contact payload.
2. Replace Continue anchor with submit flow that writes data before redirecting to `/book/seats`.
3. Define server-side validation mirror (same rules as client JS) and error-binding model for SSR.
4. Decide durable storage target for booking draft data (session vs repository/database table).
5. Include saved passenger/contact values in subsequent steps (`/book/seats`, future `/confirm-and-pay`).

---

## 8) Testing and maintenance

### Recommended tests (add as you evolve features)
- Unit: multi-stop `buildRouteBlocks` edge cases (midnight crossings).
- Integration: invalid/missing `depart`, unknown airport text.
- Integration: return-trip passenger and inbound search URLs (already partially covered).

### CSV maintenance
- Template rows: **pipe-separated** leg times and flight numbers must have length **`stops + 1`**; stopover code list length must equal **`stops`**.
- **`legFlightNumbers`:** any consistent per-leg strings work for loading; they feed the `GA###` hash (see §3.2a). Prefer distinct values per leg within a template so seeds differ. Do not expect the raw CSV strings to appear on cards.
- Prefer **5-minute** multiples in templates to match snapping.
- After pricing rule changes, update `cabinFareSet` / `enforceFareInvariants` and tests together.

---

## 9) Known resilience patterns

- Invalid search → empty state, no exception.
- Missing JS → cards and Route details still usable (native `<details>`).
- Fare invariant guard prevents impossible column prices in the UI.
- bfcache: fare animation reset on `pageshow`.

---

*Keep this file in sync when renaming template keys, `data-*` attributes used by `flights-results.js`, or CSV column layouts.*
