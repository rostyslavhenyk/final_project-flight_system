import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mindrot.jbcrypt.BCrypt

class PasswordHashTest {

    @Test
    fun `hashed password is not equal to plain text`() {
        val password = "mypassword123"
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertNotEquals(password, hashed)
    }

    @Test
    fun `correct password passes bcrypt check`() {
        val password = "mypassword123"
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertTrue(BCrypt.checkpw(password, hashed))
    }

    @Test
    fun `wrong password fails bcrypt check`() {
        val password = "mypassword123"
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertFalse(BCrypt.checkpw("wrongpassword", hashed))
    }

    @Test
    fun `empty password fails check against real hash`() {
        val password = "mypassword123"
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertFalse(BCrypt.checkpw("", hashed))
    }

    @Test
    fun `two hashes of same password are different`() {
        val password = "mypassword123"
        val hash1 = BCrypt.hashpw(password, BCrypt.gensalt())
        val hash2 = BCrypt.hashpw(password, BCrypt.gensalt())
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `password with special characters hashes correctly`() {
        val password = "p@ssw0rd!£$%"
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertTrue(BCrypt.checkpw(password, hashed))
    }

    @Test
    fun `very long password hashes correctly`() {
        val password = "a".repeat(100)
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        assertTrue(BCrypt.checkpw(password, hashed))
    }
}
