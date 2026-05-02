package routes

import auth.UserSession
import data.TicketRepository
import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
    post("/help/tickets") { call.handleCreateHelpTicket() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_help_load", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Help",
                    "ticketCreated" to (request.queryParameters["ticket"] == "created"),
                    "ticketError" to request.queryParameters["error"],
                ),
            )
        val template = pebbleEngine.getTemplate("user/help/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleCreateHelpTicket() {
    timed("T3_help_ticket_create", jsMode()) {
        val formUpload = receiveTicketFormUpload()
        val form = formUpload.fields
        val email = form["email"]?.trim().orEmpty()
        val subject = form["subject"]?.trim().orEmpty()
        val message = form["message"]?.trim().orEmpty()
        val session = sessions.get<UserSession>()
        val user = session?.let { UserRepository.get(it.id) } ?: UserRepository.getByEmail(email)

        if (formUpload.error != null) {
            respondRedirect("/help?error=image#contact")
            return@timed
        }

        if (user == null) {
            respondRedirect("/help?error=account#contact")
            return@timed
        }

        if (subject.isBlank() || message.isBlank()) {
            respondRedirect("/help?error=missing#contact")
            return@timed
        }

        val ticket =
            TicketRepository.create(
                userID = user.id,
                subject = subject,
                message = message,
                status = "OPEN",
                priority = "NORMAL",
                source = "USER",
            )

        saveTicketImages(ticket.ticketID, formUpload.images)

        respondRedirect("/help?ticket=created#contact")
    }
}
