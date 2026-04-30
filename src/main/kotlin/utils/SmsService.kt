package utils

import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber

object SmsService {

    // get these from twilio.com after signing up for a free trial
    private val accountSid = "your-twilio-account-sid"
    private val authToken = "your-twilio-auth-token"
    private val twilioNumber = "+1234567890"

    init {
        Twilio.init(accountSid, authToken)
    }

    fun sendVerificationCode(toPhone: String, code: String) {
        try {
            Message.creator(
                PhoneNumber(toPhone),
                PhoneNumber(twilioNumber),
                "Your Glide Airways verification code is: $code. Expires in 10 minutes."
            ).create()
        } catch (e: Exception) {
            println("Failed to send SMS: ${e.message}")
        }
    }

    fun sendPasswordResetCode(toPhone: String, code: String) {
        try {
            Message.creator(
                PhoneNumber(toPhone),
                PhoneNumber(twilioNumber),
                "Your Glide Airways password reset code is: $code. If you did not request this, ignore this message."
            ).create()
        } catch (e: Exception) {
            println("Failed to send SMS: ${e.message}")
        }
    }
}
