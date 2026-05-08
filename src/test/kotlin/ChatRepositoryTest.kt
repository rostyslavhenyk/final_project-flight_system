import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import data.ChatMessage

class ChatRepositoryTest {

    // we test the data class directly since database tests need a running server
    @Test
    fun `chat message has correct sender name`() {
        val msg = ChatMessage(1, 1, "Mahdi", "Hello", false, System.currentTimeMillis())
        assertEquals("Mahdi", msg.senderName)
    }

    @Test
    fun `chat message isStaff is false for customer`() {
        val msg = ChatMessage(1, 1, "Mahdi", "Hello", false, System.currentTimeMillis())
        assertFalse(msg.isStaff)
    }

    @Test
    fun `chat message isStaff is true for staff`() {
        val msg = ChatMessage(1, 1, "Support Team", "Hi!", true, System.currentTimeMillis())
        assertTrue(msg.isStaff)
    }

    @Test
    fun `chat message stores message text correctly`() {
        val msg = ChatMessage(1, 1, "Mahdi", "I need help with my booking", false, System.currentTimeMillis())
        assertEquals("I need help with my booking", msg.message)
    }

    @Test
    fun `chat message can have empty message`() {
        val msg = ChatMessage(1, 1, "Mahdi", "", false, System.currentTimeMillis())
        assertEquals("", msg.message)
    }

    @Test
    fun `chat message timestamp is positive`() {
        val msg = ChatMessage(1, 1, "Mahdi", "Hello", false, System.currentTimeMillis())
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `two messages can have different user ids`() {
        val msg1 = ChatMessage(1, 1, "Mahdi", "Hello", false, System.currentTimeMillis())
        val msg2 = ChatMessage(2, 2, "Other", "Hey", false, System.currentTimeMillis())
        assertNotEquals(msg1.userId, msg2.userId)
    }
}