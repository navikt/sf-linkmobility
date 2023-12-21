package no.nav.sf.linkmobility

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.sf.linkmobility.token.AccessTokenHandler
import no.nav.sf.linkmobility.token.DefaultAccessTokenHandler
import org.http4k.client.ApacheClient

private val log = KotlinLogging.logger { }

class Application(val accessTokenHandler: AccessTokenHandler = DefaultAccessTokenHandler()) {

    val httpClient = ApacheClient()

    fun start() {
        log.info { "Starting app" }
        naisAPIServer(8080).start()
        // refreshLoop() // Not necessary - but could prefetch if desired
    }

    tailrec fun refreshLoop() {
        runBlocking { delay(60000) } // 1 min
        accessTokenHandler.refreshToken()
        runBlocking { delay(900000) } // 15 min

        refreshLoop()
    }
}
