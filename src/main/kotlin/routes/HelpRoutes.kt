package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.baseModel
import utils.EmailService
import java.io.StringWriter
import utils.jsMode
import utils.timed

fun Route.helpRoutes() {
    get("/help") { call.handleHelpLoad() }
    post("/help/refund") { call.handleRefundRequest() }
}

private suspend fun ApplicationCall.handleHelpLoad() {
    timed("T0_about_us", jsMode()) {
        val model =
            baseModel(
                mapOf(
                    "title" to "Help",
                ),
            )
        val template = pebbleEngine.getTemplate("user/help/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

// handles refund request form and sends email to support
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

    // send email to support team
    EmailService.sendRefundRequest(
        customerEmail = email,
        subject = "Refund Request - $ref",
        body = "Name: $firstname $lastname\nEmail: $email\nBooking Ref: $ref\nReason: $reason\nDetails: $details"
    )

    // send confirmation to customer
    EmailService.sendRefundConfirmation(email, firstname, ref)

    respondText("submitted", status = HttpStatusCode.OK)
}
