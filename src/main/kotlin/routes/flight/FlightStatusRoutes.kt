package routes.flight

import data.flight.FlightStatusSearchRepository
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import routes.renderTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import utils.jsMode
import utils.timed

internal const val STATUS_RESULTS_PAGE_SIZE = 10
private const val FLIGHT_NUMBER_SUGGESTION_LIMIT = 25

private fun encodeQueryComponent(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

internal fun flightStatusQueryString(
    mode: String,
    date: LocalDate,
    anyDate: Boolean,
    flightDigits: String,
    fromRaw: String,
    toRaw: String,
    page: Int,
): String =
    buildString {
        append("mode=").append(encodeQueryComponent(mode.trim()))
        append("&date=").append(date)
        if (anyDate) {
            append("&anyDate=1")
        }
        when {
            mode.equals("route", ignoreCase = true) -> {
                append("&from=").append(encodeQueryComponent(fromRaw))
                append("&to=").append(encodeQueryComponent(toRaw))
            }
            flightDigits.isNotBlank() -> {
                append("&flightNumber=").append(encodeQueryComponent(flightDigits))
            }
        }
        append("&page=").append(page.coerceAtLeast(1))
    }

internal suspend fun ApplicationCall.handleFlightStatusPage() {
    timed("T0_flight_status_page", jsMode()) {
        renderTemplate("user/flights/status/index.peb", flightStatusPageModel(request.queryParameters))
    }
}

/** JSON autocomplete for flight digits. */
internal suspend fun ApplicationCall.handleFlightNumberSuggest() {
    val typedDigits = request.queryParameters["q"].orEmpty()
    val suggestions =
        FlightStatusSearchRepository.suggestFlightNumberDigits(
            typedDigits,
            FLIGHT_NUMBER_SUGGESTION_LIMIT,
        )
    val json =
        buildString {
            append('[')
            suggestions.forEachIndexed { index, suggestedDigits ->
                if (index > 0) {
                    append(',')
                }
                append('"')
                append(suggestedDigits)
                append('"')
            }
            append(']')
        }
    respondText(json, ContentType.Application.Json)
}
