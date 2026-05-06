package routes.staff

import data.UserRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import routes.renderTemplate
import utils.isStaffAdmin
import utils.jsMode
import utils.timed

fun Route.staffAccountsRoutes() {
    get("/staff-accounts") { call.handleStaffAccountsLoad() }
    post("/staff-accounts") { call.handleStaffAccountsPost() }
}

private suspend fun ApplicationCall.handleStaffAccountsLoad(
    statusMessage: String? = null,
    statusTone: String = "info",
) {
    timed("T4_staff_accounts_load", jsMode()) {
        if (!isStaffAdmin()) {
            respondText("Forbidden", ContentType.Text.Plain, HttpStatusCode.Forbidden)
            return@timed
        }

        renderTemplate(
            "staff/staff-accounts/index.peb",
            mapOf(
                "title" to "Create Staff Account",
                "statusMessage" to statusMessage,
                "statusTone" to statusTone,
            ),
        )
    }
}

private suspend fun ApplicationCall.handleStaffAccountsPost() {
    timed("T4_staff_accounts_create", jsMode()) {
        if (!isStaffAdmin()) {
            respondText("Forbidden", ContentType.Text.Plain, HttpStatusCode.Forbidden)
            return@timed
        }

        val params = receiveParameters()
        val firstname = params["firstname"].orEmpty()
        val lastname = params["lastname"].orEmpty()
        val email = params["email"].orEmpty()
        val password = params["password"].orEmpty()

        fun missing(field: String) = "Please fill in a $field."

        val error =
            when {
                firstname.isBlank() -> missing("first name")
                lastname.isBlank() -> missing("last name")
                email.isBlank() -> missing("email")
                password.isBlank() -> missing("password")
                UserRepository.getByEmail(email) != null -> "A user already exists with that email."
                else -> null
            }

        if (error != null) {
            handleStaffAccountsLoad(error, "error")
            return@timed
        }

        val created = UserRepository.add(firstname, lastname, 1, email, password)
        handleStaffAccountsLoad(
            "Created staff account for ${created.firstname} ${created.lastname}.",
            "success",
        )
    }
}
