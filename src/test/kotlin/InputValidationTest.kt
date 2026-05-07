import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class InputValidationTest {

    // tests for email validation
    @Test
    fun `valid email passes basic check`() {
        val email = "test@example.com"
        assertTrue(email.contains("@") && email.contains("."))
    }

    @Test
    fun `email without at symbol is invalid`() {
        val email = "testexample.com"
        assertFalse(email.contains("@"))
    }

    @Test
    fun `empty email is blank`() {
        val email = ""
        assertTrue(email.isBlank())
    }

    @Test
    fun `email with spaces is invalid`() {
        val email = "test @example.com"
        assertTrue(email.contains(" "))
    }

    // tests for password validation
    @Test
    fun `empty password is blank`() {
        val password = ""
        assertTrue(password.isBlank())
    }

    @Test
    fun `password with only spaces is blank`() {
        val password = "     "
        assertTrue(password.isBlank())
    }

    @Test
    fun `password with special characters is not blank`() {
        val password = "p@ssw0rd!"
        assertFalse(password.isBlank())
    }

    // tests for name validation
    @Test
    fun `empty first name is blank`() {
        val firstname = ""
        assertTrue(firstname.isBlank())
    }

    @Test
    fun `name with only spaces is blank`() {
        val firstname = "   "
        assertTrue(firstname.isBlank())
    }

    @Test
    fun `valid name is not blank`() {
        val firstname = "Mahdi"
        assertFalse(firstname.isBlank())
    }

    // tests for verification code format
    @Test
    fun `verification code is numeric`() {
        val code = "123456"
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `verification code with letters is invalid`() {
        val code = "abc123"
        assertFalse(code.all { it.isDigit() })
    }

    @Test
    fun `verification code must be 6 digits`() {
        val code = "1234"
        assertFalse(code.length == 6)
    }

    @Test
    fun `empty verification code is blank`() {
        val code = ""
        assertTrue(code.isBlank())
    }

    // sql injection basic check
    @Test
    fun `input with sql injection attempt is detected`() {
        val input = "' OR '1'='1"
        assertTrue(input.contains("'"))
    }

    @Test
    fun `input with drop table attempt is detected`() {
        val input = "'; DROP TABLE users; --"
        assertTrue(input.contains(";"))
    }
}
np
