/**
 * Single source for how many calendar days ahead of “today” count as “upcoming” for flight status and suggest.
 *
 * **Used by**
 * - [data.flight.FlightSearchRepository] — departure window for flight-number digit suggest and related date bounds.
 * - [FlightStatusQuery] (routes) — Pebble map `flightStatusSuggestDays` and the list of status query dates.
 * - [routes.FlightStatusFormatting] — copy such as “next N days” / no-results messages on the status UI.
 *
 * **Grep:** `FLIGHT-SYSTEM-TWEAKS`
 */
package data.flight

const val FLIGHT_STATUS_UPCOMING_DAY_COUNT = 7
