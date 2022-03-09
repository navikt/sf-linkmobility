package no.nav.sf.linkmobility

import io.prometheus.client.Gauge

data class Workmetrics(
    val requestCounterTotal: Gauge = Gauge
        .build()
        .name("request_counter_total")
        .help("request_counter_total")
        .register()
)

val workmetrics = Workmetrics()
