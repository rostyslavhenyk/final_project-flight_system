package routes.staff

import data.FlightRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.pebbleEngine
import utils.baseModel
import java.io.StringWriter

fun Route.staffFlightsRoutes() {
    get("/flights") { call.handleStaffFlights() }
}

private suspend fun ApplicationCall.handleStaffFlights() {
    val model =
        baseModel(
            mapOf(
                "title" to "Staff Flights",
                "flights" to FlightRepository.allFull(),
            ),
        )

    val template = pebbleEngine.getTemplate("staff/flights/index.peb")
    val writer = StringWriter()

    template.evaluate(writer, model)

    respondText(writer.toString(), ContentType.Text.Html)
}
