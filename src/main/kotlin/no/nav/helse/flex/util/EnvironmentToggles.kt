package no.nav.helse.flex.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @Value("\${NAIS_CLUSTER_NAME}") private val naisCluster: String,
) {
    fun isProduction() = "prod-gcp" == naisCluster

    fun isDevGcp() = "dev-gcp" == naisCluster
}
