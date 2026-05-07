import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import utils.VerificationStore

class VerificationStoreTest {

    @Test
    fun `generated code is 6 digits`() {
        val code = VerificationStore.generateCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `saved code can be verified`() {
        VerificationStore.saveCode("test@test.com", "123456")
        assertTrue(VerificationStore.verifyCode("test@test.com", "123456"))
    }

    @Test
    fun `wrong code fails verification`() {
        VerificationStore.saveCode("test@test.com", "123456")
        assertFalse(VerificationStore.verifyCode("test@test.com", "999999"))
    }

    @Test
    fun `code is removed after verification`() {
        VerificationStore.saveCode("test@test.com", "123456")
        VerificationStore.removeCode("test@test.com")
        assertFalse(VerificationStore.verifyCode("test@test.com", "123456"))
    }

    @Test
    fun `verifying non existent key returns false`() {
        assertFalse(VerificationStore.verifyCode("nobody@test.com", "123456"))
    }

    @Test
    fun `empty code fails verification`() {
        VerificationStore.saveCode("test@test.com", "123456")
        assertFalse(VerificationStore.verifyCode("test@test.com", ""))
    }

    @Test
    fun `empty key fails verification`() {
        assertFalse(VerificationStore.verifyCode("", "123456"))
    }

    @Test
    fun `code is case sensitive`() {
        VerificationStore.saveCode("test@test.com", "123456")
        assertFalse(VerificationStore.verifyCode("TEST@TEST.COM", "123456"))
    }
}
