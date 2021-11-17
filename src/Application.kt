package no.nav.sf.linkmobility

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readBytes
import io.ktor.features.ContentNegotiation
import io.ktor.features.origin
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.response.respondTextWriter
import io.ktor.routing.get
import io.ktor.routing.routing
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.security.KeyStore
import java.security.PrivateKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import token.TokenResponse

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

data class ApplicationState(
    var running: Boolean = true,
    var initialized: Boolean = false
)

private val log = KotlinLogging.logger { }

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val username = System.getenv("username")
    val password = System.getenv("password")

    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the 'api/' path"
            validate { credentials ->
                if (credentials.name == username && credentials.password == password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    routing {
        static("swagger") {
            resources("static")
            defaultResource("static/index.html")
        }
        get("/internal/is_alive") {
            call.respondText("I'm alive! :)")
        }
        get("/internal/is_ready") {
            call.respondText("I'm ready! :)")
        }
        get("/internal/prometheus") {
            val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
            }
        }
        get("api/registersms") {
            val res = fetchAccessTokenAndInstanceUrl()
            call.respond(HttpStatusCode.OK, "first ${res.first} second ${res.second}")
        }
        authenticate("auth-basic") {
            get("api/ping") {
                val headers = call.request.headers.entries().map { "${it.key} : ${it.value}" }.joinToString("\n")

                val origin =
                    "${call.request.origin.uri}, ${call.request.origin.host}, ${call.request.origin.port}, ${call.request.origin.method}, ${call.request.origin.remoteHost}, ${call.request.origin.scheme}"

                log.info { "Authorized call to Ping. Header info:\n$headers\n\n$origin" }
                // log.info { "Req information request: ${call.request}, headers: ${call.request.headers}, orig: ${call.request.origin}, orig remoteHost: ${call.request.origin.remoteHost}, orig host: ${call.request.origin.host}, orig pory: ${call.request.origin.port}, orig uri: ${call.request.origin.uri}" }
                call.respond(HttpStatusCode.OK, "Successfully pinged!")
                /*
            if (containsValidToken(call.request)) {
                log.info { "Authorized call to Arkiv" }
                val requestBody = call.receive<Array<ArkivModel>>()
                call.respond(HttpStatusCode.Created, addArchive(requestBody))
            } else {
                log.info { "Arkiv call denied - missing valid token" }
                call.respond(HttpStatusCode.Unauthorized)
            }
             */
            }
        }
    }
}

/*
fun isAuthenticated(call: ApplicationCall): Boolean {
    val auth = call.request.header(HttpHeaders.Authorization)

    if (auth != null) {
        return if (auth.startsWith("Bearer ")) {
            val authToken = auth.replace("Bearer ", "")

            // Use the auth to verify that auth token is valid with Azure AD
            true
        } else {
            false
        }
    }

    return false
}

 */

val tokenHost = System.getenv("SF_TOKENHOST")

val SFClientID = System.getenv("SFClientID")
val SFUsername = System.getenv("SFUsername")
val keystoreB64 = System.getenv("keystoreJKSB64")
val keystorePassword = System.getenv("KeystorePassword")
val privateKeyAlias = System.getenv("PrivateKeyAlias")
val privateKeyPassword = System.getenv("PrivateKeyPassword")

val objectMapper: ObjectMapper = ObjectMapper()

// Spring:
// val requestFactory = SimpleClientHttpRequestFactory()
// Spring:
// val restTemplate : RestTemplate
val client = HttpClient(Apache)

suspend fun fetchAccessTokenAndInstanceUrl(): Pair<String, String> {
    val claim = JWTClaim(iss = SFClientID, aud = tokenHost, sub = SFUsername, exp = ((System.currentTimeMillis() / 1000) + 300).toString())
    val privateKey = PrivateKeyFromBase64Store(ksB64 = keystoreB64, ksPwd = keystorePassword, pkAlias = privateKeyAlias, pkPwd = privateKeyPassword)
    val claimWithHeaderJsonUrlSafe = "${objectMapper.writeValueAsString(JWTClaimHeader("RS256")).encodeB64UrlSafe()}.${objectMapper.writeValueAsString(claim).encodeB64UrlSafe()}"
    val fullClaimSignature = privateKey.sign(claimWithHeaderJsonUrlSafe.toByteArray())

    /*
    val headers = HttpHeaders()
    headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

    val body : MultiValueMap<String, String> = LinkedMultiValueMap()
    body.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
    body.add("assertion", "$claimWithHeaderJsonUrlSafe.$fullClaimSignature")

    val entity: HttpEntity<MultiValueMap<String, String>> = HttpEntity(body, headers)
*/
    for (retry in 1..4) {
        try {
            // val response: ResponseEntity<String> = restTemplate.exchange(tokenHost,
            //    HttpMethod.POST, entity, String::class.java)

            val statement = client.submitForm<HttpStatement>(
                url = tokenHost,
                formParameters = Parameters.build {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    append("assertion", "$claimWithHeaderJsonUrlSafe.$fullClaimSignature")
                }
            )

            val response = statement.execute()

            if (response.status == HttpStatusCode.OK) {
                val accessTokenResponse = objectMapper.readValue(response.readBytes(), TokenResponse::class.java)!!
                return Pair(accessTokenResponse.access_token, accessTokenResponse.instance_url)
            }
        } catch (e: Exception) {
            log.error("Attempt to fetch access token $retry of 3 failed by ${e.message} stack: ${e.printStackTrace()}}")
            runBlocking { delay(retry * 1000L) }
        }
    }
    return Pair("", "")
}

fun PrivateKeyFromBase64Store(ksB64: String, ksPwd: String, pkAlias: String, pkPwd: String): PrivateKey {
    return KeyStore.getInstance("JKS").apply { load(ksB64.decodeB64().inputStream(), ksPwd.toCharArray()) }.run {
        getKey(pkAlias, pkPwd.toCharArray()) as PrivateKey
    }
}

fun PrivateKey.sign(data: ByteArray): String {
    return this.let { java.security.Signature.getInstance("SHA256withRSA").apply {
        initSign(it)
        update(data)
    }.run {
        sign().encodeB64()
    } }
}

data class JWTClaim(
    val iss: String,
    val aud: String,
    val sub: String,
    val exp: String
)

data class JWTClaimHeader(val alg: String)

fun ByteArray.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this)
fun String.decodeB64(): ByteArray = org.apache.commons.codec.binary.Base64.decodeBase64(this)
fun String.encodeB64UrlSafe(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this.toByteArray())
