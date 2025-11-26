@file:Suppress("ktlint:standard:filename", "ktlint:standard:property-naming")

package no.nav.sf.linkmobility

const val secret_KEYSTORE_JKS_B64 = "KEYSTORE_JKS_B64"
const val secret_KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD"
const val secret_PRIVATE_KEY_ALIAS = "PRIVATE_KEY_ALIAS"
const val secret_PRIVATE_KEY_PASSWORD = "PRIVATE_KEY_PASSWORD"
const val secret_SF_CLIENT_ID = "SF_CLIENT_ID"
const val secret_SF_USERNAME = "SF_USERNAME"
const val secret_USERNAME = "USERNAME"
const val secret_PASSWORD = "PASSWORD"

const val config_SF_TOKENHOST = "SF_TOKENHOST"

/**
 * Shortcut for fetching environment variables
 */
fun env(name: String): String = System.getenv(name) ?: throw NullPointerException("Missing env $name")
