package data.flight

private const val FLIGHT_NUMBER_DIGITS = 5

internal fun flightNumberFor(id: Int): String = "GA" + id.toString().padStart(FLIGHT_NUMBER_DIGITS, '0')
