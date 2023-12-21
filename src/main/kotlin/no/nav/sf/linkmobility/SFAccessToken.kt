package no.nav.sf.linkmobility

import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import java.security.KeyStore
import java.security.PrivateKey

private val log = KotlinLogging.logger { }

val gson = Gson()

val SFClientID = System.getenv("SFClientID")
val SFUsername = System.getenv("SFUsername")
val SFTokenHost = System.getenv("SF_TOKENHOST")

data class AccessToken(
    val access_token: String = "",
    val scope: String = "",
    val instance_url: String,
    val id: String = "",
    val token_type: String = "",
    val issued_at: String = "",
    val signature: String = ""
)

fun AccessToken.ageInMinutes(): Int {
    return ((System.currentTimeMillis() - this.issued_at.toLong()) / 60000L).toInt()
}

val httpClient = ApacheClient()

data class JWTClaim(
    val iss: String,
    val aud: String,
    val sub: String,
    val exp: String
)

val keystoreB64 = System.getenv("keystoreJKSB64")
val keystorePassword = System.getenv("KeystorePassword")
val privateKeyAlias = System.getenv("PrivateKeyAlias")
val privateKeyPassword = System.getenv("PrivateKeyPassword")

fun fetchAccessTokenAndInstanceUrl(): Pair<String, String> {
    val claim = JWTClaim(
        iss = SFClientID,
        aud = SFTokenHost,
        sub = SFUsername,
        exp = ((System.currentTimeMillis() / 1000) + 300).toString()
    )
    val privateKey = PrivateKeyFromBase64Store(
        ksB64 = keystoreB64,
        ksPwd = keystorePassword,
        pkAlias = privateKeyAlias,
        pkPwd = privateKeyPassword
    )
    val claimWithHeaderJsonUrlSafe = "${
    gson.toJson(JWTClaimHeader("RS256")).encodeB64UrlSafe()
    }.${gson.toJson(claim).encodeB64UrlSafe()}"
    val fullClaimSignature = privateKey.sign(claimWithHeaderJsonUrlSafe.toByteArray())

    val accessTokenRequest = org.http4k.core.Request(Method.POST, "$SFTokenHost/services/oauth2/no.nav.sf.linkmobility.token")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .query("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
        .query("assertion", "$claimWithHeaderJsonUrlSafe.$fullClaimSignature")

    for (retry in 1..4) {
        try {
            val response = httpClient(accessTokenRequest)

            if (response.status == Status.OK) {
                val accessTokenResponse = gson.fromJson(response.bodyString(), AccessToken::class.java)!!
                return Pair(accessTokenResponse.access_token, accessTokenResponse.instance_url)
            }
        } catch (e: Exception) {
            log.error("Attempt to fetch access no.nav.sf.linkmobility.token $retry of 3 failed by ${e.message} stack: ${e.printStackTrace()}}")
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
    return this.let {
        java.security.Signature.getInstance("SHA256withRSA").apply {
            initSign(it)
            update(data)
        }.run {
            sign().encodeB64()
        }
    }
}

data class JWTClaimHeader(val alg: String)

fun ByteArray.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this)
fun String.decodeB64(): ByteArray = org.apache.commons.codec.binary.Base64.decodeBase64(this)
fun String.encodeB64UrlSafe(): String =
    org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this.toByteArray())
