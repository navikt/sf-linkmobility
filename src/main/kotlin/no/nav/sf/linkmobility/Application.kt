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
        // refreshLoop() On high traffic this can make sure access token is always ready to go
    }

    tailrec fun refreshLoop() {
        runBlocking { delay(60000) } // 1 min
        accessTokenHandler.refreshToken()
        runBlocking { delay(900000) } // 15 min

        refreshLoop()
    }
}
