package utils

import java.time.Instant

// i'm using a map to store codes temporarily in memory
// the key is the email or phone number, value is the code and when it expires
object VerificationStore {

    private val codes = mutableMapOf<String, Pair<String, Instant>>()

    // generates a random 6 digit number as a string
    fun generateCode(): String {
        val code = (100000..999999).random()
        return code.toString()
    }

    fun saveCode(key: String, code: String) {
        // expires after 10 minutes
        val expiry = Instant.now().plusSeconds(600)
        codes[key] = Pair(code, expiry)
    }

    fun verifyCode(key: String, submittedCode: String): Boolean {
        val entry = codes[key]

        // if no code found return false
        if (entry == null) return false

        val savedCode = entry.first
        val expiry = entry.second

        // check if expired
        if (Instant.now().isAfter(expiry)) {
            codes.remove(key)
            return false
        }

        return savedCode == submittedCode
    }

    fun removeCode(key: String) {
        codes.remove(key)
    }
}
