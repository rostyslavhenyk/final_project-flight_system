# Homepage handover — Glide Airways (`/`)

This handover follows the same style as the flights handover: user flow first, then implementation details.  
Use [`flights-handover.md`](flights-handover.md) for search-results logic and schedule generation.

---

## 1) Website tutorial (user-facing)

### 1.1 Navigation and structure
- Global header (`Flights`, `Membership`, `Help`, auth).
- Skip link and visible focus styles.
- Footer (legal, contact, social).

### 1.2 Book-a-trip panel (homepage core)
- Airport autocomplete for **Leaving from** and **Going to** (options from server-rendered list + resolution logic shared with search).
- Trip type: **one-way** / **return**.
- Cabin + passenger modal (class, adults, children) writing to **hidden inputs** (canonical submitted values).
- Depart / return date pickers (Flatpickr) writing **local** `YYYY-MM-DD` to hidden fields.
- Submit → `GET /search-flights` with query string built from those fields.

### 1.3 Error checks (client side)
- Block submit if required fields missing.
- Block **same origin and destination**.
- Return trip: require return date and enforce **return ≥ depart**.
- Airport inputs must match known options (no free-text fake airports).

### 1.4 Offers carousel and media
- **Server-rendered** offer cards on first paint (works before JS).
- Optional refresh via `/api/latest-offers` when “Leaving from” resolves to a valid origin code; debounced.
- Carousel controls; image stack hover (respects reduced motion).
- Lightbox gallery with keyboard support.

### 1.5 Graceful fallback behavior
- API failure → SSR offers remain.
- If JS is slow or disabled, the form is still visible and usable.
- Clearing origin → offers can fall back to default SSR set.

---

## 2) Request flow (end to end)

1. `GET /` → [`HomepageRoutes.kt`](../src/main/kotlin/routes/HomepageRoutes.kt) builds model: airports for dropdown/autocomplete labels, default origin context, **offers list**.
2. Pebble renders [`templates/homepage/index.peb`](../src/main/resources/templates/homepage/index.peb).
3. Browser loads `custom.css`, `homepage.css`, deferred [`homepage.js`](../src/main/resources/static/js/homepage.js).
4. JS enhances the page: autocomplete, modal controls, Flatpickr, form validation, offer refresh, carousel/lightbox.

**Why SSR first?** The user sees content immediately, without waiting for JS. It also helps accessibility and avoids blank-page failures.

---

## 3) Data source: offers come from CSV, not pricing formulas

**Important difference from flight search:** Homepage offer prices are static values from [`data/offers.csv`](../data/offers.csv), loaded by [`OffersRepository.kt`](../src/main/kotlin/data/OffersRepository.kt).  
There is no generated/hash-based pricing on this page.

| Concept | Homepage offers | Flight search (`/search-flights`) |
|--------|-------------------|-----------------------------------|
| Storage | `offers.csv` rows | `flight_schedule_templates.csv` + **runtime** expansion |
| Price source | Column `price_gbp` (integer) | Formulas in `FlightScheduleRepository` + cabin rules in `FlightsRoutes` |
| Filtering | By `origin_code` for “from Manchester” etc. | By resolved IATA + date |

**Why mention this?** So you do not waste time looking for price-generation logic in homepage code.

### 3.1 Latest offers carousel (SSR + `/api/latest-offers`)

The carousel cards (Hong Kong first, then others) are built by [`LatestOffersService`](../src/main/kotlin/data/LatestOffersService.kt), not directly from `offers.csv`.  
For each destination in [`data/offer_destinations.csv`](../data/offer_destinations.csv), the service calculates distance from the selected origin using [`GeoRepository.coordinatesForAirport`](../src/main/kotlin/data/GeoRepository.kt) and [`data/destination_geo.csv`](../data/destination_geo.csv), then creates a deterministic "from GBP" value.

**Important maintenance rule:** Every IATA code in the "Leaving from" list must exist in [`data/airports_geo.csv`](../data/airports_geo.csv).  
If not, distance becomes `null`, the API returns no cards, and [`homepage.js`](../src/main/resources/static/js/homepage.js) shows the "Fares are not available..." message.

The **Latest offers API** (`GET /api/latest-offers?origin=…`) is wired in [`Main.kt`](../src/main/kotlin/Main.kt); SSR still passes `offerCards` from the same service for the default origin in [`HomepageRoutes.kt`](../src/main/kotlin/routes/HomepageRoutes.kt).

---

## 4) Code map and responsibilities

### 4.1 Template — [`homepage/index.peb`](../src/main/resources/templates/homepage/index.peb)
- Semantic sections: search, offers, promos, lightbox.
- **Listbox / combobox** scaffolding for airports with ARIA wired for JS.
- **Hidden inputs** hold canonical values (`trip`, `cabinClass`, `adults`, `children`, dates) separate from visible labels.

**Why hidden fields?** One source of truth for submit; visible strings can be formatted for humans.

### 4.2 Kotlin operations map (backend logic)

Kotlin backend logic is part of homepage behavior (not only template/JS).

- `handleLoadPage` in [`HomepageRoutes.kt`](../src/main/kotlin/routes/HomepageRoutes.kt)
  - resolves optional `origin` query
  - builds SSR model (`airports`, `offerCards`, labels, defaults)
  - renders homepage template via Pebble
- `/api/latest-offers` endpoint in [`Main.kt`](../src/main/kotlin/Main.kt)
  - validates input query
  - calls `LatestOffersService`
  - returns JSON used by `homepage.js`
- `LatestOffersService` in [`LatestOffersService.kt`](../src/main/kotlin/data/LatestOffersService.kt)
  - builds carousel card data
  - computes deterministic price bands from distance and origin
- Data repositories in [`OffersRepository.kt`](../src/main/kotlin/data/OffersRepository.kt), [`AirportRepository.kt`](../src/main/kotlin/data/AirportRepository.kt), [`GeoRepository.kt`](../src/main/kotlin/data/GeoRepository.kt)
  - load CSV airport/offer/geo data for SSR and API

### 4.3 Script — [`homepage.js`](../src/main/resources/static/js/homepage.js)

| Initializer | Role |
|-------------|------|
| `initAutocomplete` | Prefix filter on options, empty state, focus behaviour. |
| `initTripCombobox` | Trip type toggles hidden `trip`. |
| `initCabinModal` | Stepper values → hidden cabin / passenger counts. |
| `initDatePickers` | Flatpickr → hidden `YYYY-MM-DD`; return **minDate** tied to depart. |
| `initFlightSearchValidation` | Final checks before submit. |
| `initOffersFromLeavingFrom` + `renderOffersFromJson` | Debounced API refetch and DOM replace. |
| `initOfferLightbox`, `initOffersCarousel` | Media and keyboard UX. |

**Why two validation layers?** UI blocks bad input early, but the server still handles messy query strings safely.

### 4.4 Styles — `custom.css` + `homepage.css`
- Global shell and focus in `custom.css`.
- Homepage-only layout (search card, dropdown layering, carousel) in `homepage.css` to avoid leaking rules to flight results.

---

## 5) Design notes

- **SSR first, JS second** — reliable first paint; JS is progressive enhancement.
- **Canonical hidden fields** — avoid parsing human-readable labels on submit.
- **Local calendar dates** from Flatpickr reduce timezone confusion in this teaching project.
- **Offers** are editable CSV data; **flight prices** are generated by rules. Keep both paths documented.

---

## 6) Maintenance checklist

- **New airport:** Update [`airports_display.csv`](../data/airports_display.csv) (and [`airports.csv`](../data/airports.csv) if you mirror the homepage list), **and** add `lat`/`lon` for that code in [`airports_geo.csv`](../data/airports_geo.csv) so latest-offer cards and any geo features keep working.
- **New offer row (book-a-trip strip):** Edit `offers.csv`; origins there still control the small “Economy from £…” rows if you use them.
- **Form field rename:** Update hidden input `name`s, `homepage.js` writers, and any integration tests together.

---

## 7) Related documentation

- Flight search UI, **route details** structure, **schedule CSV + formulas**: [`flights-handover.md`](flights-handover.md).

---

*If IDs, `name` attributes, or API paths change, update this guide so onboarding stays accurate.*
