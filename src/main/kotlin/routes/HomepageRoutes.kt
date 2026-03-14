package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.homepageRoutes() {
    get("/") { call.handleLoadPage() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    timed("T1_homepage_load", jsMode()) {
        val pebble = getEngine()

        val airports =
            listOf(
                "Manchester (MAN)",
                "London Heathrow (LHR)",
                "London Gatwick (LGW)",
                "Hong Kong (HKG)",
                "Bangkok (BKK)",
                "Denpasar (DPS)",
                "Manila (MNL)",
                "Singapore (SIN)",
                "Dubai (DXB)",
                "New York (JFK)",
            )

        val model =
            mapOf(
                "title" to "Homepage",
                "airports" to airports,
            )

        val template = pebble.getTemplate("homepage/index.peb")
        val writer = StringWriter()
        fullEvaluate(template, writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}
