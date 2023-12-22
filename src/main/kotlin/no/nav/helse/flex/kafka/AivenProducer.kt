package no.nav.helse.flex.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("default")
class AivenProducer(
    private val aivenKafkaConfig: AivenKafkaConfig,
    @Value("\${KAFKA_SCHEMA_REGISTRY}") private val kafkaSchemaRegistryUrl: String,
    @Value("\${KAFKA_SCHEMA_REGISTRY_USER}") private val kafkaSchemaUsername: String,
    @Value("\${KAFKA_SCHEMA_REGISTRY_PASSWORD}") private val kafkaSchemaPassword: String,
) {
    private fun avroProducerConfig() =
        mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
            ProducerConfig.RETRIES_CONFIG to 10,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl,
            KafkaAvroSerializerConfig.USER_INFO_CONFIG to "$kafkaSchemaUsername:$kafkaSchemaPassword",
            KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
            KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS to false,
            SaslConfigs.SASL_MECHANISM to "PLAIN",
        ) + aivenKafkaConfig.commonConfig()

    private fun simpleProducerConfig() =
        mapOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 10,
            ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
        ) + aivenKafkaConfig.commonConfig()

    @Bean
    fun beskjedKafkaProducer() = KafkaProducer<NokkelInput, BeskjedInput>(avroProducerConfig())

    @Bean
    fun doneKafkaProducer() = KafkaProducer<NokkelInput, DoneInput>(avroProducerConfig())

    @Bean
    fun meldingProducer() = KafkaProducer<String, String>(simpleProducerConfig())
}
