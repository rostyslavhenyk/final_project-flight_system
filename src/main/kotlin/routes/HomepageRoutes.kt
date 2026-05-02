package routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import utils.isStaff
import utils.jsMode
import utils.timed
import java.io.StringWriter

fun Route.homepageRoutes() {
    get("/") { call.handleLoadPage() }
}

private suspend fun ApplicationCall.handleLoadPage() {
    timed("T0_homepage_load", jsMode()) {
        if (isStaff()) {
            respondRedirect("/staff")
            return@timed
        }

        val model =
            baseModel(
                mapOf("title" to "Homepage"),
            )

        val template = pebbleEngine.getTemplate("user/homepage/index.peb")
        val writer = StringWriter()

        template.evaluate(writer, model)

        respondText(
            writer.toString(),
            io.ktor.http.ContentType.Text.Html,
        )
    }
}
