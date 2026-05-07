package utils

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

// sends emails via gmail smtp
// replace senderEmail and senderPassword before testing
object EmailService {
    private val senderEmail = System.getenv("GLIDE_EMAIL_ADDRESS").orEmpty()
    private val senderPassword = System.getenv("GLIDE_EMAIL_APP_PASSWORD").orEmpty()

    private fun isConfigured(): Boolean = senderEmail.isNotBlank() && senderPassword.isNotBlank()

    private fun getSession(): Session {
        val props = Properties()
        props["mail.smtp.host"] = "smtp.gmail.com"
        props["mail.smtp.port"] = "587"
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.starttls.enable"] = "true"

        return Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(senderEmail, senderPassword)
            },
        )
    }

    fun sendVerificationCode(
        toEmail: String,
        code: String,
    ): Boolean =
        try {
            if (!isConfigured()) {
                println("Email is not configured. Set GLIDE_EMAIL_ADDRESS and GLIDE_EMAIL_APP_PASSWORD.")
                false
            } else {
                val session = getSession()
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(senderEmail))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                message.subject = "Your Glide Airways Verification Code"
                message.setText(
                    "Hi,\n\nYour verification code is: $code\n\nThis code expires in 10 minutes.\n\nGlide Airways",
                )
                Transport.send(message)
                true
            }
        } catch (e: Exception) {
            println("Failed to send email: ${e.message}")
            false
        }

    fun sendPasswordResetCode(
        toEmail: String,
        code: String,
    ): Boolean =
        try {
            if (!isConfigured()) {
                println("Email is not configured. Set GLIDE_EMAIL_ADDRESS and GLIDE_EMAIL_APP_PASSWORD.")
                false
            } else {
                val session = getSession()
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(senderEmail))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                message.subject = "Glide Airways Password Reset"
                message.setText(
                    "Hi,\n\nYour password reset code is: $code\n\n" +
                        "If you did not request this, ignore this email.\n\nGlide Airways",
                )
                Transport.send(message)
                true
            }
        } catch (e: Exception) {
            println("Failed to send email: ${e.message}")
            false
        }
}
