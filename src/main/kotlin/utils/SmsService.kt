package utils

import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber

// handles sending sms messages using twilio
// sign up at twilio.com to get the account sid, auth token and phone number
object SmsService {
    private val accountSid = System.getenv("TWILIO_ACCOUNT_SID").orEmpty()
    private val authToken = System.getenv("TWILIO_AUTH_TOKEN").orEmpty()
    private val twilioNumber = System.getenv("TWILIO_PHONE_NUMBER").orEmpty()

    private fun isConfigured(): Boolean = accountSid.isNotBlank() && authToken.isNotBlank() && twilioNumber.isNotBlank()

    private fun initTwilio() {
        Twilio.init(accountSid, authToken)
    }

    fun sendVerificationCode(
        toPhone: String,
        code: String,
    ): Boolean =
        try {
            if (!isConfigured()) {
                println("SMS is not configured. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_PHONE_NUMBER.")
                false
            } else {
                initTwilio()
                Message
                    .creator(
                        PhoneNumber(toPhone),
                        PhoneNumber(twilioNumber),
                        "Your Glide Airways verification code is: $code. Expires in 10 minutes.",
                    ).create()
                true
            }
        } catch (e: Exception) {
            println("Failed to send SMS: ${e.message}")
            false
        }

    fun sendPasswordResetCode(
        toPhone: String,
        code: String,
    ): Boolean =
        try {
            if (!isConfigured()) {
                println("SMS is not configured. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_PHONE_NUMBER.")
                false
            } else {
                initTwilio()
                Message
                    .creator(
                        PhoneNumber(toPhone),
                        PhoneNumber(twilioNumber),
                        "Your Glide Airways password reset code is: $code. If you did not request this, ignore this message.",
                    ).create()
                true
            }
        } catch (e: Exception) {
            println("Failed to send SMS: ${e.message}")
            false
        }
}
