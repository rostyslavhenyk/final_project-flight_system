package staticchecks

import testsupport.withClue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

class JavaScriptConsistencyTest {
    @Test
    fun `booking seat javascript endpoints match server route paths`() {
        val script = staticText("js/book-seats.js")

        withClue("seat JS still calls unavailable validate hold and release endpoints") {
            assertTrue(script.contains("/book/seats/unavailable"))
            assertTrue(script.contains("/book/seats/validate"))
            assertTrue(script.contains("hold"))
            assertTrue(script.contains("release"))
            assertTrue(script.contains("'/book/seats/' + action"))
        }
    }

    @Test
    fun `payment javascript keeps stripe endpoints and payment error hook`() {
        val script = staticText("js/book-payment.js")
        val template = templateText("user/flights/step-4-payment/index.peb")

        withClue("payment JS calls setup and confirm endpoints") {
            assertTrue(script.contains("/book/payment/setup-intent"))
            assertTrue(script.contains("/book/payment/confirm"))
        }
        withClue("payment template keeps DOM hooks used by JS") {
            assertTrue(template.contains("""id="stripe-card-element""""))
            assertTrue(template.contains("""id="pay-now-button""""))
        }
        withClue("payment JS owns the user-facing failure text") {
            assertTrue(script.contains("Card setup or booking confirmation failed"))
        }
    }

    @Test
    fun `chat widget javascript endpoints match chat routes and template hooks`() {
        val script = staticText("js/help.js")
        val template = templateText("user/help/index.peb")

        withClue("help JS calls chat send and messages endpoints") {
            assertTrue(script.contains("/chat/send"))
            assertTrue(script.contains("/chat/messages"))
        }
        withClue("help template keeps chat widget DOM hooks") {
            listOf("chatWidget", "chatBody", "chatMessages", "chatInput").forEach { id ->
                assertTrue(template.contains("""id="$id""""))
            }
        }
    }

    @Test
    fun `forgot password javascript endpoints match verification routes`() {
        val script = staticText("js/forgot-password.js")

        withClue("forgot password JS calls send verify and reset endpoints") {
            listOf("/forgot-password/send", "/forgot-password/verify", "/forgot-password/reset").forEach { endpoint ->
                assertTrue(script.contains(endpoint))
            }
        }
    }
}

private val root: Path = Paths.get("").toAbsolutePath()
private val staticRoot: Path = root.resolve("src/main/resources/static")
private val templateRoot: Path = root.resolve("src/main/resources/templates")

private fun staticText(relative: String): String = Files.readString(staticRoot.resolve(relative))

private fun templateText(relative: String): String = Files.readString(templateRoot.resolve(relative))
