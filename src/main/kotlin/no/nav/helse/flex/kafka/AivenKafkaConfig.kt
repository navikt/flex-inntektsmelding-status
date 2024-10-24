package no.nav.helse.flex.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

private const val JAVA_KEYSTORE = "JKS"
private const val PKCS12 = "PKCS12"
const val DITT_SYKEFRAVAER_MELDING_TOPIC = "flex." + "ditt-sykefravaer-melding"
const val MINSIDE_BRUKERVARSEL = "min-side.aapen-brukervarsel-v1"
const val INNTEKTSMELDING_STATUS_TOPIC = "tbd." + "inntektsmeldingstatus"
const val SIS_TOPIC = "tbd." + "sis"
const val INNTEKTSMELDING_STATUS_TESTDATA_TOPIC = "flex." + "inntektsmeldingstatus-testdata"
const val SYKEPENGESOKNAD_TOPIC = "flex" + ".sykepengesoknad"
const val AUDIT_TOPIC = "flex.auditlogging"

@Configuration
class AivenKafkaConfig(
    @Value("\${KAFKA_BROKERS}") private val kafkaBrokers: String,
    @Value("\${KAFKA_SECURITY_PROTOCOL:SSL}") private val kafkaSecurityProtocol: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
) {
    fun commonConfig() =
        mapOf(
            BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        ) + securityConfig()

    private fun securityConfig() =
        mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            // Disables server host name verification.
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to JAVA_KEYSTORE,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to PKCS12,
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )
}
