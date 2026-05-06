package routes.flight

import data.flight.FlightSearchRepository.FlightSortOption
import java.util.Locale

/** Maps `sort` query value to [FlightSortOption]; unknown values fall back to Recommended. */
internal fun parseFlightSortOption(raw: String?): FlightSortOption =
    when (raw?.lowercase(Locale.UK)) {
        "departure" -> FlightSortOption.DepartureTime
        "arrival" -> FlightSortOption.ArrivalTime
        "duration" -> FlightSortOption.Duration
        "fare" -> FlightSortOption.Fare
        "stops" -> FlightSortOption.Stops
        else -> FlightSortOption.Recommended
    }

/** Reverse of [parseFlightSortOption] for building `sort=` links. */
internal fun FlightSortOption.toParam(): String =
    when (this) {
        FlightSortOption.Recommended -> "recommended"
        FlightSortOption.DepartureTime -> "departure"
        FlightSortOption.ArrivalTime -> "arrival"
        FlightSortOption.Duration -> "duration"
        FlightSortOption.Fare -> "fare"
        FlightSortOption.Stops -> "stops"
    }

/** Href for each sort tab; clicking the active tab toggles asc/desc (except Recommended). */
internal fun buildSortLinks(
    base: Map<String, String>,
    current: FlightSortOption,
    ascending: Boolean,
): Map<FlightSortOption, String> =
    FlightSortOption.entries.associateWith { key ->
        val nextOrder =
            when (key) {
                FlightSortOption.Recommended -> "asc"
                current -> if (ascending) "desc" else "asc"
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
