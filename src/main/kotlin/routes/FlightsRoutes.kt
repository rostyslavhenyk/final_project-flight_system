package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.flightsRoutes() {
    get("/flights") { call.handleFlightsList() }
}

private suspend fun ApplicationCall.handleFlightsList() {
    timed("T0_flights_list", jsMode()) {
        val pebble = getEngine()

        val model =
            mapOf(
                "title" to "Flights",
            )

        val template = pebble.getTemplate("flights/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
