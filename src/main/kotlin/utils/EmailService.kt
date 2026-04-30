package utils

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

object EmailService {

    // replace these with real credentials before testing
    private val senderEmail = "yourapp@gmail.com"
    private val senderPassword = "your-app-password-here"

    // sets up the email session with gmail smtp settings
    private fun getSession(): Session {
        val props = Properties()
        props["mail.smtp.host"] = "smtp.gmail.com"
        props["mail.smtp.port"] = "587"
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.starttls.enable"] = "true"

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(senderEmail, senderPassword)
            }
        })

        return session
    }

    fun sendVerificationCode(toEmail: String, code: String) {
        try {
            val session = getSession()
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(senderEmail))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = "Your Glide Airways Verification Code"
            message.setText("Hi,\n\nYour verification code is: $code\n\nThis code expires in 10 minutes.\n\nGlide Airways")

            Transport.send(message)
        } catch (e: Exception) {
            // just print the error for now, can improve error handling later
            println("Failed to send email: ${e.message}")
        }
    }

    fun sendPasswordResetCode(toEmail: String, code: String) {
        try {
            val session = getSession()
            val message = MimeMessage(session)

            message.setFrom(InternetAddress(senderEmail))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = "Glide Airways Password Reset"
            message.setText("Hi,\n\nYou requested a password reset.\n\nYour code is: $code\n\nIf you did not request this, ignore this email.\n\nGlide Airways")

            Transport.send(message)
        } catch (e: Exception) {
            println("Failed to send email: ${e.message}")
        }
    }
}
