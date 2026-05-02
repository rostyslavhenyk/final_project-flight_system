package routes.staff

import auth.UserSession
import data.TicketRepository
import data.TicketImageRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondFile
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import routes.pebbleEngine
import routes.receiveTicketFormUpload
import routes.saveTicketImages
import utils.baseModel
import utils.jsMode
import utils.timed
import java.io.File
import java.io.StringWriter

private const val UNKNOWN_STATUS_ORDER = 4

fun Route.staffTicketsRoutes() {
    get("/tickets") { call.handleStaffTickets() }
    post("/tickets") { call.handleCreateStaffTicket() }
    get("/tickets/images/{id}") { call.handleTicketImage() }
    get("/tickets/{id}") { call.handleTicketDetails() }
    post("/tickets/{id}/status") { call.handleUpdateTicketStatus() }
}

private suspend fun ApplicationCall.handleStaffTickets() {
    timed("T5_staff_tickets_list", jsMode()) {
        val activeTab = request.queryParameters["tab"] ?: "requested"
        val created = request.queryParameters["created"] == "1"
        val error = request.queryParameters["error"]
        val query = request.queryParameters["q"]?.trim().orEmpty()
        val status = request.queryParameters["status"]?.trim().orEmpty()
        val staffTickets = TicketRepository.allFullBySource("STAFF").filterTickets(query, status).orderTickets()
        val requestedTickets = TicketRepository.allFullBySource("USER").filterTickets(query, status).orderTickets()

        val model =
            baseModel(
                mapOf(
                    "title" to "Staff Tickets",
                    "activeTab" to activeTab,
                    "created" to created,
                    "error" to error,
                    "query" to query,
                    "statusFilter" to status,
                    "staffTickets" to staffTickets,
                    "requestedTickets" to requestedTickets,
                ),
            )

        val template = pebbleEngine.getTemplate("staff/tickets/index.peb")
        val writer = StringWriter()

        template.evaluate(writer, model)

        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private fun List<data.TicketFull>.filterTickets(
    query: String,
    status: String,
): List<data.TicketFull> =
    filter {
        val matchesQuery =
            query.isBlank() ||
                it.ticket.ticketID
                    .toString()
                    .contains(query, ignoreCase = true) ||
                it.ticket.subject.contains(query, ignoreCase = true) ||
                it.ticket.priority.contains(query, ignoreCase = true) ||
                it.user.firstname.contains(query, ignoreCase = true) ||
                it.user.lastname.contains(query, ignoreCase = true) ||
                it.user.email.contains(query, ignoreCase = true)

        val matchesStatus =
            status.isBlank() ||
                it.ticket.status.equals(status, ignoreCase = true) ||
                it.ticket.priority.equals(status, ignoreCase = true)

        matchesQuery && matchesStatus
    }

private fun List<data.TicketFull>.orderTickets(): List<data.TicketFull> =
    sortedWith(
        compareBy<data.TicketFull> { it.ticket.status.statusOrder() }
            .thenByDescending { it.ticket.updatedAt },
    )

private fun String.statusOrder(): Int =
    when (uppercase()) {
        "OPEN" -> 0
        "IN_PROGRESS" -> 1
        "RESOLVED" -> 2
        "CLOSED" -> 3
        else -> UNKNOWN_STATUS_ORDER
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
            respondRedirect("/staff/tickets?tab=create&error=image")
            return@timed
        }

        if (staffUserID == null || subject.isBlank() || message.isBlank()) {
            respondRedirect("/staff/tickets?tab=create&error=missing")
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

private suspend fun ApplicationCall.handleTicketDetails() {
    timed("T5_staff_ticket_detail", jsMode()) {
        val ticketID = parameters["id"]?.toIntOrNull()
        val ticket = ticketID?.let { TicketRepository.getFull(it) }

        if (ticket == null) {
            respondRedirect("/staff/tickets?error=missing-ticket")
            return@timed
        }

        val model =
            baseModel(
                mapOf(
                    "title" to "Ticket ${ticket.ticket.ticketID}",
                    "ticketFull" to ticket,
                    "ticketImages" to TicketImageRepository.allForTicket(ticket.ticket.ticketID),
                    "updated" to (request.queryParameters["updated"] == "1"),
                ),
            )

        val template = pebbleEngine.getTemplate("staff/tickets/detail.peb")
        val writer = StringWriter()

        template.evaluate(writer, model)

        respondText(writer.toString(), ContentType.Text.Html)
    }
}

private suspend fun ApplicationCall.handleTicketImage() {
    val imageID = parameters["id"]?.toIntOrNull()
    val image = imageID?.let { TicketImageRepository.get(it) }
    val file = image?.let { File(it.path) }

    if (image == null || file == null || !file.isFile) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondFile(file)
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
