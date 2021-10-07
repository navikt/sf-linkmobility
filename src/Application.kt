package no.nav.sf.linkmobility

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.features.ContentNegotiation
import io.ktor.features.origin
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
import mu.KotlinLogging

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
