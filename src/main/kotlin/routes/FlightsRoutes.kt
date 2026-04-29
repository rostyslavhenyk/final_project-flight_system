package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.flightsRoutes() {
    get("/flights") { call.handleFlightsList() }
}

private suspend fun ApplicationCall.handleFlightsList() {
    timed("T0_flights_list", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Flights",
                ),
            )

        val template = pebbleEngine.getTemplate("user/flights/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
