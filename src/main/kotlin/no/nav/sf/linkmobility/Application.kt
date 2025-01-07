package no.nav.sf.linkmobility

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import no.nav.sf.linkmobility.token.AccessTokenHandler
import no.nav.sf.linkmobility.token.DefaultAccessTokenHandler
import org.http4k.client.ApacheClient
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer
import java.io.File
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

class Application(val accessTokenHandler: AccessTokenHandler = DefaultAccessTokenHandler()) {

    val httpClient = ApacheClient()

    fun start() {
        log.info { "Starting app" }
        apiServer().start()
    }

    private fun apiServer(port: Int = 8080): Http4kServer = api().asServer(ApacheServer(port))

    private fun api(): HttpHandler = routes(
        "/api/sms" bind Method.POST to basicAuthFilter().then(smsHandler),
        "/api/ping" bind Method.GET to basicAuthFilter().then { Response(Status.OK).body("Successfully pinged!") },
        "/internal/is_alive" bind Method.GET to { Response(Status.OK) },
        "/internal/is_ready" bind Method.GET to { Response(Status.OK) },
        "/internal/prometheus" bind Method.GET to metricsHttpHandler
    )

    private fun basicAuthFilter(expectedUsername: String = System.getenv("username"), expectedPassword: String = System.getenv("password")): Filter = Filter { next ->
        {
            val credentials = it.header("Authorization")?.removePrefix("Basic")?.trim()?.fromBase64()?.split(":")
            if (credentials?.size == 2 && credentials[0] == expectedUsername && credentials[1] == expectedPassword) {
                next(it)
            } else {
                Response(Status.UNAUTHORIZED).header("WWW-Authenticate", "Basic realm=\"Restricted Area\"")
            }
        }
    }

    private fun String.fromBase64(): String = String(java.util.Base64.getDecoder().decode(this))

    private val smsHandler: HttpHandler = { r ->
        log.info { "Authorized call to /api/sms" }
        Metrics.requestCount.inc()

        val uri = "${application.accessTokenHandler.instanceUrl}/services/apexrest/receiveSMS"

        val request = Request(Method.POST, uri)
            .header("Authorization", "Bearer ${application.accessTokenHandler.accessToken}")
            .body(r.body)

        val response = application.httpClient(request)
        File("/tmp/latestforward-${response.status.code}").writeText(
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) +
                "\n\n" +
                request.toMessage() +
                "\n\n" +
                response.toMessage()
        )
        if (response.status.code == 500) {
            log.error { "Sms call made to Salesforce with response code 500. Parse error in Salesforce?" }
        } else {
            log.info { "Sms call made to Salesforce with response code ${response.status} " }
        }
        Metrics.responseCount.labels(response.status.code.toString()).inc()
        response
    }

    private val metricsHttpHandler: HttpHandler = {
        try {
            val str = StringWriter()
            TextFormat.write004(str, CollectorRegistry.defaultRegistry.metricFamilySamples())
            val result = str.toString()
            if (result.isEmpty()) {
                Response(Status.NO_CONTENT)
            } else {
                Response(Status.OK).body(result)
            }
        } catch (e: Exception) {
            log.error { "/prometheus failed writing metrics - ${e.message}" }
            Response(Status.INTERNAL_SERVER_ERROR)
        }
    }
}
