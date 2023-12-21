package no.nav.sf.linkmobility

import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
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

private val log = KotlinLogging.logger { }

fun naisAPI(): HttpHandler = routes(
    // "/static" bind static(ResourceLoader.Classpath("/static")),
    "/api/ping" bind Method.GET to basicAuthFilter().then { Response(Status.OK).body("Successfully pinged!") },
    "/api/sms" bind Method.POST to basicAuthFilter().then { r ->
        log.info { "Authorized call to /api/sms" }
        workMetrics.requestCount.inc()

        val uri = "${application.accessTokenHandler.instanceUrl}/services/apexrest/receiveSMS"

        val request = Request(Method.POST, uri)
            .header("Authorization", "Bearer ${application.accessTokenHandler.accessToken}")
            .body(r.body)

        File("/tmp/latestrequest").writeText(request.toMessage())
        val response = application.httpClient(request)
        File("/tmp/latestresponse").writeText(response.toMessage())
        response
    },
    // "/api/at" bind Method.GET to { Response(Status.OK).body(application.accessTokenHandler.accessToken) },
    "/internal/is_alive" bind Method.GET to { Response(Status.OK) },
    "/internal/is_ready" bind Method.GET to { Response(Status.OK) },
    "/internal/prometheus" bind Method.GET to {
        runCatching {
            StringWriter().let { str ->
                TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                str
            }.toString()
        }
            .onFailure {
                log.error { "/prometheus failed writing metrics - ${it.localizedMessage}" }
            }
            .getOrDefault("")
            .responseByContent()
    }
)

fun basicAuthFilter(expectedUsername: String = System.getenv("username"), expectedPassword: String = System.getenv("password")): Filter = Filter { next ->
    {
        val credentials = it.header("Authorization")?.removePrefix("Basic")?.trim()?.fromBase64()?.split(":")
        if (credentials?.size == 2 && credentials[0] == expectedUsername && credentials[1] == expectedPassword) {
            next(it)
        } else {
            Response(Status.UNAUTHORIZED).header("WWW-Authenticate", "Basic realm=\"Restricted Area\"")
        }
    }
}

fun String.fromBase64(): String = String(java.util.Base64.getDecoder().decode(this))

private fun String.responseByContent(): Response =
    if (this.isNotEmpty()) Response(Status.OK).body(this) else Response(Status.NO_CONTENT)

fun naisAPIServer(port: Int): Http4kServer = naisAPI().asServer(ApacheServer(port))
