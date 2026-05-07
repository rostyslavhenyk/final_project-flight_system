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

    private val senderEmail = "glideairways.support@gmail.com"
    private val senderPassword = "yekdqnxsmwedvlnz"

    private fun getSession(): Session {
        val props = Properties()
        props["mail.smtp.host"] = "smtp.gmail.com"
        props["mail.smtp.port"] = "587"
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.starttls.enable"] = "true"

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(senderEmail, senderPassword)
            }
        })
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
            message.setText("Hi,\n\nYour password reset code is: $code\n\nIf you did not request this, ignore this email.\n\nGlide Airways")
            Transport.send(message)
        } catch (e: Exception) {
            println("Failed to send email: ${e.message}")
        }
    }
    fun sendRefundRequest(customerEmail: String, subject: String, body: String) {
        try {
        val session = getSession()
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(senderEmail))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(senderEmail))
        message.subject = subject
        message.setText("Refund request from $customerEmail\n\n$body")
        Transport.send(message)
    } catch (e: Exception) {
        println("Failed to send refund request email: ${e.message}")
    }
}

    fun sendRefundConfirmation(toEmail: String, firstname: String, ref: String) {
        try {
        val session = getSession()
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(senderEmail))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
        message.subject = "Glide Airways - Refund Request Received"
        message.setText("Hi $firstname,\n\nWe have received your refund request for booking $ref.\n\nWe will get back to you within 3-5 business days.\n\nGlide Airways")
        Transport.send(message)
    } catch (e: Exception) {
        println("Failed to send refund confirmation: ${e.message}")
    }
}
}
