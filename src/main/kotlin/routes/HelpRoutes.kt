package routes

import auth.UserSession
import data.TicketRepository
import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import routes.staff.receiveTicketFormUpload
import routes.staff.saveTicketImages
import utils.EmailService
import utils.jsMode
import utils.timed

private const val REFUND_MESSAGE_LENGTH = 2000
private const val REFUND_REFERENCE_LENGTH = 32

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
    get("/help/refund/status") { call.handleRefundStatus() }
    post("/help/tickets") { call.handleCreateHelpTicket() }
    post("/help/refund") { call.handleRefundRequest() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_help_load", jsMode()) {
        respondHelpPage(
            ticketCreated = request.queryParameters["ticket"] == "created",
            ticketError = request.queryParameters["error"],
        )
    }
}

private suspend fun ApplicationCall.handleRefundStatus() {
    val bookingReference = request.queryParameters["ref"].orEmpty()
    val email = request.queryParameters["email"].orEmpty()
    val ticket = TicketRepository.findRefundByReference(bookingReference, email)

    if (bookingReference.isBlank() || email.isBlank()) {
        respondText("Enter your booking reference and booking email.", status = HttpStatusCode.BadRequest)
        return
    }

    if (ticket == null) {
        respondText("No tracked refund request was found for those details.", status = HttpStatusCode.NotFound)
        return
    }

    val statusText = ticket.ticket.status.replace('_', ' ')
    respondText("Refund ticket ${ticket.ticket.ticketID} is $statusText. Last updated ${ticket.updatedAtText}.")
}

private suspend fun ApplicationCall.handleRefundRequest() {
    val params = receiveParameters()
    val firstname = params["firstname"] ?: ""
    val lastname = params["lastname"] ?: ""
    val email = params["email"] ?: ""
    val ref = params["ref"] ?: ""
    val reason = params["reason"] ?: ""
    val details = params["details"] ?: ""

    if (email.isBlank() || ref.isBlank()) {
        respondText("Missing required fields", status = HttpStatusCode.BadRequest)
        return
    }

    EmailService.sendRefundRequest(
        customerEmail = email,
        subject = "Refund Request - $ref",
        body = "Name: $firstname $lastname\nEmail: $email\nBooking Ref: $ref\nReason: $reason\nDetails: $details",
    )

    createRefundTicketIfPossible(
        email = email,
        firstname = firstname,
        lastname = lastname,
        bookingReference = ref,
        reason = reason,
        details = details,
    )

    EmailService.sendRefundConfirmation(email, firstname, ref)

    respondText("submitted", status = HttpStatusCode.OK)
}

private fun createRefundTicketIfPossible(
    email: String,
    firstname: String,
    lastname: String,
    bookingReference: String,
    reason: String,
    details: String,
) {
    val user = UserRepository.getByEmail(email.trim()) ?: return
    val reference = bookingReference.trim().take(REFUND_REFERENCE_LENGTH)
    val message =
        listOf(
            "Name: $firstname $lastname".trim(),
            "Email: ${email.trim()}",
            "Booking Ref: $reference",
            "Reason: ${reason.trim()}",
            "Details: ${details.trim()}",
        ).joinToString("\n").take(REFUND_MESSAGE_LENGTH)

    TicketRepository.create(
        userID = user.id,
        subject = "Refund Request - $reference",
        message = message,
        status = "OPEN",
        priority = "HIGH",
        source = "USER",
        bookingReference = reference,
    )
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

        respondRedirect("/help?ticket=created")
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
