package no.nav.sf.linkmobility

import io.prometheus.client.Gauge

data class WMetrics(
    val requestCount: Gauge = Metrics.registerGauge("request_count"),
    val tokenRefreshCount: Gauge = Metrics.registerGauge("token_refresh_count"),
    val issues: Gauge = Metrics.registerGauge("issues")
) {
    fun clearAll() {
        requestCount.clear()
        issues.clear()
    }
}

val workMetrics = WMetrics()
