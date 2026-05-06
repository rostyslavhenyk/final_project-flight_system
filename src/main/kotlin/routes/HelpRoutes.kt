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
import routes.staff.receiveTicketFormUpload
import routes.staff.saveTicketImages
import utils.jsMode
import utils.timed

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
    post("/help/tickets") { call.handleCreateHelpTicket() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_help_load", jsMode()) {
        respondHelpPage(
            ticketCreated = request.queryParameters["ticket"] == "created",
            ticketError = request.queryParameters["error"],
        )
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
            respondHelpPage(ticketError = "image", ticketForm = form)
            return@timed
        }

        if (user == null) {
            respondHelpPage(ticketError = "account", ticketForm = form)
            return@timed
        }

        if (subject.isBlank() || message.isBlank()) {
            respondHelpPage(ticketError = "missing", ticketForm = form)
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

private suspend fun ApplicationCall.respondHelpPage(
    ticketCreated: Boolean = false,
    ticketError: String? = null,
    ticketForm: Map<String, String> = emptyMap(),
) {
    renderTemplate(
        "user/help/index.peb",
        mapOf(
            "title" to "Help",
            "ticketCreated" to ticketCreated,
            "ticketError" to ticketError,
            "ticketForm" to ticketForm,
        ),
    )
}
