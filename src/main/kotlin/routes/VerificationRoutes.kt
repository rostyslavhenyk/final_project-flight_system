package routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.StringWriter
import utils.jsMode
import utils.timed
import utils.baseModel
import utils.EmailService
import utils.SmsService
import utils.VerificationStore
import data.UserRepository
import org.mindrot.jbcrypt.BCrypt

// verification and password reset routes
fun Route.verificationRoutes() {
    get("/forgot-password") { call.handleForgotPasswordLoad() }
    post("/forgot-password/send") { call.handleForgotPasswordSend() }
    post("/forgot-password/verify") { call.handleForgotPasswordVerify() }
    post("/forgot-password/reset") { call.handlePasswordReset() }
    post("/verify/send-email") { call.handleSendEmailCode() }
    post("/verify/check-email") { call.handleCheckEmailCode() }
    post("/verify/send-sms") { call.handleSendSmsCode() }
    post("/verify/check-sms") { call.handleCheckSmsCode() }
}

fun ApplicationCall.createVerifyStatus(message: String): String =
    """
    <div id="verify-status" class="auth-status" hx-swap-oob="true" role="status"
         aria-live="polite" aria-atomic="true">$message</div>
    """.trimIndent()

private suspend fun ApplicationCall.handleForgotPasswordLoad() {
    timed("T0_forgot_password", jsMode()) {
        val model = baseModel(mapOf("title" to "Forgot Password"))
        val template = pebbleEngine.getTemplate("user/forgot-password/index.peb")
        val writer = StringWriter()
        template.evaluate(writer, model)
        respondText(writer.toString(), ContentType.Text.Html)
    }
}

// sends reset code to email or phone
private suspend fun ApplicationCall.handleForgotPasswordSend() {
    timed("T1_forgot_password_send", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]
        val phone = params["phone"]

        if (email.isNullOrBlank() && phone.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Please enter an email or phone number"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val code = VerificationStore.generateCode()
        val errorMessage =
            if (!email.isNullOrBlank()) {
                forgotPasswordEmailError(email, code)
            } else {
                forgotPasswordSmsError(phone.orEmpty(), code)
            }
        if (errorMessage != null) {
            respondVerifyStatus(errorMessage)
            return@timed
        }

        respondVerifyStatus("Code sent successfully")
    }
}

private fun forgotPasswordEmailError(
    email: String,
    code: String,
): String? {
    val user = UserRepository.getByEmail(email)
    if (user == null) return "No account found with that email"
    VerificationStore.saveCode(email, code)
    return if (EmailService.sendPasswordResetCode(email, code)) null else "Email sending is not configured"
}

private fun forgotPasswordSmsError(
    phone: String,
    code: String,
): String? {
    val user = UserRepository.getByPhone(phone)
    if (user == null) return "No account found with that phone number"
    VerificationStore.saveCode(phone, code)
    return if (SmsService.sendPasswordResetCode(phone, code)) null else "SMS sending is not configured"
}

private suspend fun ApplicationCall.respondVerifyStatus(message: String) {
    respondText(
        createVerifyStatus(message),
        ContentType.Text.Html,
        status = HttpStatusCode.OK,
    )
}

private suspend fun ApplicationCall.handleForgotPasswordVerify() {
    timed("T2_forgot_password_verify", jsMode()) {
        val params = receiveParameters()
        val key = params["email"] ?: params["phone"]
        val code = params["code"]

        if (key.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Email or phone is required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (code.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Please enter the code"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val valid = VerificationStore.verifyCode(key, code)

        if (!valid) {
            respondText(
                createVerifyStatus("Invalid or expired code"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        respondText(
            createVerifyStatus("Code verified"),
            ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}

// resets password after code is verified
private suspend fun ApplicationCall.handlePasswordReset() {
    timed("T3_password_reset", jsMode()) {
        val params = receiveParameters()
        val key = params["email"] ?: params["phone"]
        val newPassword = params["newPassword"]
        val confirmPassword = params["confirmPassword"]

        if (key.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Email or phone is required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (newPassword.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Please enter a new password"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        if (newPassword != confirmPassword) {
            respondText(
                createVerifyStatus("Passwords do not match"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val user =
            if (key.contains("@")) {
                UserRepository.getByEmail(key)
            } else {
                UserRepository.getByPhone(key)
            }
        if (user == null) {
            respondText(
                createVerifyStatus("User not found"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        UserRepository.updatePassword(user.id, hashedPassword)
        VerificationStore.removeCode(key)

        response.headers.append("HX-Redirect", "/login")
        respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleSendEmailCode() {
    timed("T0_send_email_code", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]

        if (email.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Email is required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val code = VerificationStore.generateCode()
        VerificationStore.saveCode(email, code)
        if (!EmailService.sendVerificationCode(email, code)) {
            respondText(
                createVerifyStatus("Email sending is not configured"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        respondText(
            createVerifyStatus("Code sent to $email"),
            ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}

private suspend fun ApplicationCall.handleCheckEmailCode() {
    timed("T1_check_email_code", jsMode()) {
        val params = receiveParameters()
        val email = params["email"]
        val code = params["code"]

        if (email.isNullOrBlank() || code.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Email and code are required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val valid = VerificationStore.verifyCode(email, code)

        if (!valid) {
            respondText(
                createVerifyStatus("Invalid or expired code"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        VerificationStore.removeCode(email)
        respondText(
            createVerifyStatus("Email verified"),
            ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}

private suspend fun ApplicationCall.handleSendSmsCode() {
    timed("T0_send_sms_code", jsMode()) {
        val params = receiveParameters()
        val phone = params["phone"]

        if (phone.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Phone number is required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val code = VerificationStore.generateCode()
        VerificationStore.saveCode(phone, code)
        if (!SmsService.sendVerificationCode(phone, code)) {
            respondText(
                createVerifyStatus("SMS sending is not configured"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        respondText(
            createVerifyStatus("Code sent to $phone"),
            ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}

private suspend fun ApplicationCall.handleCheckSmsCode() {
    timed("T1_check_sms_code", jsMode()) {
        val params = receiveParameters()
        val phone = params["phone"]
        val code = params["code"]

        if (phone.isNullOrBlank() || code.isNullOrBlank()) {
            respondText(
                createVerifyStatus("Phone and code are required"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        val valid = VerificationStore.verifyCode(phone, code)

        if (!valid) {
            respondText(
                createVerifyStatus("Invalid or expired code"),
                ContentType.Text.Html,
                status = HttpStatusCode.OK,
            )
            return@timed
        }

        VerificationStore.removeCode(phone)
        respondText(
            createVerifyStatus("Phone verified"),
            ContentType.Text.Html,
            status = HttpStatusCode.OK,
        )
    }
}
