package routes.staff

import auth.UserSession
import data.TicketImageRepository
import data.TicketRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondFile
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import routes.renderTemplate
import utils.jsMode
import utils.timed
import java.io.File

fun Route.staffTicketsRoutes() {
    get("/tickets") { call.handleStaffTickets() }
    post("/tickets") { call.handleCreateStaffTicket() }
    get("/tickets/images/{id}") { call.handleTicketImage() }
    get("/tickets/{id}") { call.handleTicketDetails() }
    post("/tickets/{id}/status") { call.handleUpdateTicketStatus() }
}

private suspend fun ApplicationCall.handleStaffTickets() {
    timed("T5_staff_tickets_list", jsMode()) {
        respondStaffTicketsPage(
            activeTab = request.queryParameters["tab"] ?: "requested",
            created = request.queryParameters["created"] == "1",
            error = request.queryParameters["error"],
            query = request.queryParameters["q"]?.trim().orEmpty(),
            status = request.queryParameters["status"]?.trim().orEmpty(),
        )
    }
}

private suspend fun ApplicationCall.handleCreateStaffTicket() {
    timed("T5_staff_ticket_create", jsMode()) {
        val formUpload = receiveTicketFormUpload()
        val form = formUpload.fields
        val staffUserID = sessions.get<UserSession>()?.id
        val subject = form["subject"]?.trim().orEmpty()
        val message = form["message"]?.trim().orEmpty()
        val priority = form["priority"]?.trim().orEmpty().ifBlank { "NORMAL" }
        val status = form["status"]?.trim().orEmpty().ifBlank { "OPEN" }

        if (formUpload.error != null) {
            respondStaffTicketsPage(activeTab = "create", error = "image", ticketForm = form)
            return@timed
        }

        if (staffUserID == null || subject.isBlank() || message.isBlank()) {
            respondStaffTicketsPage(activeTab = "create", error = "missing", ticketForm = form)
            return@timed
        }

        val ticket =
            TicketRepository.create(
                userID = staffUserID,
                subject = subject,
                message = message,
                status = status,
                priority = priority,
                source = "STAFF",
            )

        saveTicketImages(ticket.ticketID, formUpload.images)

        respondRedirect("/staff/tickets?tab=created&created=1")
    }
}

private suspend fun ApplicationCall.respondStaffTicketsPage(
    activeTab: String = "requested",
    created: Boolean = false,
    error: String? = null,
    query: String = "",
    status: String = "",
    ticketForm: Map<String, String> = emptyMap(),
) {
    val staffTickets = TicketRepository.searchFullBySource("STAFF", query, status)
    val requestedTickets = TicketRepository.searchFullBySource("USER", query, status)

    renderTemplate(
        "staff/tickets/index.peb",
        mapOf(
            "title" to "Staff Tickets",
            "activeTab" to activeTab,
            "created" to created,
            "error" to error,
            "query" to query,
            "statusFilter" to status,
            "staffTickets" to staffTickets,
            "requestedTickets" to requestedTickets,
            "ticketForm" to ticketForm,
        ),
    )
}

private suspend fun ApplicationCall.handleTicketDetails() {
    timed("T5_staff_ticket_detail", jsMode()) {
        val ticketID = parameters["id"]?.toIntOrNull()
        val ticket = ticketID?.let { TicketRepository.getFull(it) }

        if (ticket == null) {
            respondRedirect("/staff/tickets?error=missing-ticket")
            return@timed
        }

        renderTemplate(
            "staff/tickets/detail.peb",
            mapOf(
                "title" to "Ticket ${ticket.ticket.ticketID}",
                "ticketFull" to ticket,
                "ticketImages" to TicketImageRepository.allForTicket(ticket.ticket.ticketID),
                "updated" to (request.queryParameters["updated"] == "1"),
            ),
        )
    }
}

private suspend fun ApplicationCall.handleTicketImage() {
    timed("T5_staff_ticket_image", jsMode()) {
        val imageID = parameters["id"]?.toIntOrNull()
        val image = imageID?.let { TicketImageRepository.get(it) }
        val file = image?.let { File(it.path) }

        if (image == null || file == null || !file.isFile) {
            respond(HttpStatusCode.NotFound)
            return@timed
        }

        respondFile(file)
    }
}

private suspend fun ApplicationCall.handleUpdateTicketStatus() {
    timed("T5_staff_ticket_status_update", jsMode()) {
        val ticketID = parameters["id"]?.toIntOrNull()
        val status = receiveParameters()["status"]?.trim().orEmpty()

        if (ticketID == null || status.isBlank()) {
            respondRedirect("/staff/tickets")
            return@timed
        }

        TicketRepository.updateStatus(ticketID, status)

        respondRedirect("/staff/tickets/$ticketID?updated=1")
    }
}
