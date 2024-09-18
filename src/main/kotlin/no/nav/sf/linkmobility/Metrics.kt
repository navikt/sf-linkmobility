package no.nav.sf.linkmobility

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.hotspot.DefaultExports
import mu.KotlinLogging

object Metrics {
    private val log = KotlinLogging.logger { }
    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    fun registerCounter(name: String): Counter = Counter.build().name(name).help(name).register()

    fun registerLabelCounter(name: String, vararg labels: String): Counter =
        Counter.build().name(name).help(name).labelNames(*labels).register()

    init {
        DefaultExports.initialize()
        log.info { "Prometheus metrics are ready" }
    }
}
