package no.nav.sf.linkmobility

import io.prometheus.client.Counter

data class WMetrics(
    val requestCount: Counter = Metrics.registerCounter("requests"),
    val responseCount: Counter = Metrics.registerLabelCounter("responses", "status_code")
)

val workMetrics = WMetrics()
