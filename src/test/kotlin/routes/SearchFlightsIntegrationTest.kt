package routes

import configureLogging
import configureRouting
import configureSessions
import configureTemplating
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SearchFlightsIntegrationTest {
    @Test
    fun `first class only exposes flex and from equals flex`() =
        testApplication {
            application {
                configureLogging()
                configureTemplating()
                configureSessions()
                configureRouting()
            }

            val resp =
                client.get(
                    "/search-flights?from=Manchester%20(MAN)&to=Hong%20Kong%20(HKG)&depart=2026-04-25&trip=one-way&cabinClass=first&adults=1&children=0",
                )
            assertEquals(HttpStatusCode.OK, resp.status)
            val html = resp.bodyAsText()

            assertTrue(html.contains("First class"), "Expected first class banner")
            assertFalse(html.contains("Select Essential"), "First class should not show Essential selection")
            assertTrue(html.contains("Select Flex"), "First class should show Flex selection")

            val fromPrice = Regex("""Choose fare, from GBP ([0-9]+\.[0-9]{2})""").find(html)?.groupValues?.get(1)
            val flexPrice = Regex("""aria-label="Flex fare price">GBP ([0-9]+\.[0-9]{2})""").find(html)?.groupValues?.get(1)
            assertTrue(!fromPrice.isNullOrBlank() && !flexPrice.isNullOrBlank(), "Prices not found in response")
            assertEquals(flexPrice, fromPrice, "First class FROM price must equal Flex")
        }

    @Test
    fun `book passengers return inbound preserves leg and choose flights links to inbound search`() =
        testApplication {
            application {
                configureLogging()
                configureTemplating()
                configureSessions()
                configureRouting()
            }

            val resp =
                client.get(
                    "/book/passengers?from=Hong%20Kong%20(HKG)&to=Manchester%20(MAN)&depart=2026-05-02&return=2026-04-25&trip=return&leg=inbound&obFrom=Manchester%20(MAN)&obTo=Hong%20Kong%20(HKG)&obDepart=2026-04-25&outboundPrice=450.00&fare=flex&flight=test-fid&price=520.00",
                )
            assertEquals(HttpStatusCode.OK, resp.status)
            val html = resp.bodyAsText()
            assertTrue(
                html.contains("/search-flights") && html.contains("leg=inbound"),
                "Choose flights should reopen inbound results with leg=inbound",
            )
            assertFalse(html.contains("Change outbound flight"), "Passenger page should not show outbound shortcut link")
        }

    @Test
    fun `inbound search results include back to outbound flights link`() =
        testApplication {
            application {
                configureLogging()
                configureTemplating()
                configureSessions()
                configureRouting()
            }

            val resp =
                client.get(
                    "/search-flights?from=Hong%20Kong%20(HKG)&to=Manchester%20(MAN)&depart=2026-05-02&trip=return&return=2026-04-25&leg=inbound&obFrom=Manchester%20(MAN)&obTo=Hong%20Kong%20(HKG)&obDepart=2026-04-25&outboundPrice=450&cabinClass=economy&adults=1&children=0",
                )
            assertEquals(HttpStatusCode.OK, resp.status)
            val html = resp.bodyAsText()
            assertTrue(html.contains("Back to departing flights"), "Expected leg navigation link")
            assertTrue(
                html.contains("from=Manchester%20%28MAN%29") && html.contains("depart=2026-04-25"),
                "Outbound link should use stored outbound route and date",
            )
        }

    @Test
    fun `back to outbound preserves return date when inbound URL has empty return query`() =
        testApplication {
            application {
                configureLogging()
                configureTemplating()
                configureSessions()
                configureRouting()
            }

            val resp =
                client.get(
                    "/search-flights?from=Hong%20Kong%20(HKG)&to=Manchester%20(MAN)&depart=2026-05-02&trip=return&leg=inbound&obFrom=Manchester%20(MAN)&obTo=Hong%20Kong%20(HKG)&obDepart=2026-04-25&outboundPrice=450&cabinClass=economy&adults=1&children=0",
                )
            assertEquals(HttpStatusCode.OK, resp.status)
            val html = resp.bodyAsText()
            assertTrue(html.contains("Back to departing flights"), "Expected leg navigation link")
            assertTrue(
                html.contains("return=2026-05-02"),
                "Outbound URL must repeat the inbound travel date as return= so fare JS continues to inbound search",
            )
            assertTrue(
                html.contains("data-search-return=\"2026-05-02\""),
                "Cards need data-search-return for return-trip fare navigation",
            )
        }

    @Test
    fun `book review return trip shows departing and returning sections with chosen flights`() =
        testApplication {
            application {
                configureLogging()
                configureTemplating()
                configureSessions()
                configureRouting()
            }

            val enc: (String) -> String = { s ->
                URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")
            }

            val outboundSearch =
                client.get(
                    "/search-flights?from=London%20(LHR)&to=Los%20Angeles%20(LAX)&depart=2026-06-01&trip=return&return=2026-06-15&cabinClass=economy&adults=1&children=0&page=1",
                )
            assertEquals(HttpStatusCode.OK, outboundSearch.status)
            val obHtml = outboundSearch.bodyAsText()
            val obFid =
                Regex("""data-flight-id="([^"]+)"""").find(obHtml)?.groupValues?.get(1)
                    ?: error("expected outbound data-flight-id")
            assertTrue(obFid.isNotBlank(), "outbound flight id")

            val inboundSearch =
                client.get(
                    "/search-flights?from=Los%20Angeles%20(LAX)&to=London%20(LHR)&depart=2026-06-15&trip=return&leg=inbound&obFrom=London%20(LHR)&obTo=Los%20Angeles%20(LAX)&obDepart=2026-06-01&cabinClass=economy&adults=1&children=0&page=1&flight=" +
                        enc(obFid) +
                        "&fare=essential&outboundPrice=400.00",
                )
            assertEquals(HttpStatusCode.OK, inboundSearch.status)
            val ibHtml = inboundSearch.bodyAsText()
            val ibFid =
                Regex("""data-flight-id="([^"]+)"""").find(ibHtml)?.groupValues?.get(1)
                    ?: error("expected inbound data-flight-id")

            val review =
                client.get(
                    "/book/review?from=Los%20Angeles%20(LAX)&to=London%20(LHR)&depart=2026-06-15&trip=return&obFrom=London%20(LHR)&obTo=Los%20Angeles%20(LAX)&obDepart=2026-06-01&cabinClass=economy&adults=1&children=0&flight=" +
                        enc(ibFid) +
                        "&fare=flex&obFlight=" +
                        enc(obFid) +
                        "&obFare=essential&outboundPrice=400.00",
                )
            assertEquals(HttpStatusCode.OK, review.status)
            val reviewHtml = review.bodyAsText()
            assertTrue(reviewHtml.contains("Departing flight"), "departing section")
            assertTrue(reviewHtml.contains("Returning flight"), "returning section")
            assertTrue(
                Regex("""class="flight-card__time">\d{2}:\d{2}<""").containsMatchIn(reviewHtml),
                "review should render clock times from flight card (Pebble include `with` map needs quoted keys)",
            )
            assertTrue(reviewHtml.contains("Select another flight"), "per-leg change flight")
            assertTrue(reviewHtml.contains("Select another fare"), "per-leg change fare")
            assertTrue(reviewHtml.contains(">Continue<"), "primary continue control")
            assertTrue(
                reviewHtml.contains("London") && reviewHtml.contains("Los Angeles"),
                "route subtitles reflect chosen cities",
            )
        }
}

