package no.nav.sf.linkmobility

import mu.KotlinLogging
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status.Companion.UNAUTHORIZED

private val log = KotlinLogging.logger { }

object Application {

    val username = System.getenv("username")
    val password = System.getenv("password")

    fun basicAuthFilter(expectedUsername: String = username, expectedPassword: String = password): Filter = Filter { next ->
        {
            val credentials = it.header("Authorization")?.removePrefix("Basic")?.fromBase64()?.split(":")
            if (credentials?.size == 2 && credentials[0] == expectedUsername && credentials[1] == expectedPassword) {
                next(it)
            } else {
                Response(UNAUTHORIZED).header("WWW-Authenticate", "Basic realm=\"Restricted Area\"")
            }
        }
    }

    fun String.fromBase64(): String = String(java.util.Base64.getDecoder().decode(this))

    var accessToken: AccessToken = AccessToken("", "", "", "", "", "0", "") // Accesstoken at epoch

    fun start() {
        log.info { "Starting app" }
        naisAPIServer(8080).start()
    }
}
