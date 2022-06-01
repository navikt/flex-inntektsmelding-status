package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class AivenConsumer(
    @Value("\${aiven-kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
    @Value("\${KAFKA_SCHEMA_REGISTRY}") private val kafkaSchemaRegistryUrl: String,
    private val aivenKafkaConfig: AivenKafkaConfig,
) {

    private fun avroConsumerConfig() = mapOf(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
        ConsumerConfig.GROUP_ID_CONFIG to "flex-inntektsmelding-status",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1,
        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl,
        KafkaAvroDeserializerConfig.AUTO_REGISTER_SCHEMAS to false,
        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
    ) + aivenKafkaConfig.commonConfig()

    private fun simpleConsumerConfig() = mapOf(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.GROUP_ID_CONFIG to "flex-inntektsmelding-status",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1,
    ) + aivenKafkaConfig.commonConfig()

    @Bean
    fun aivenKafkaListenerContainerFactory(
        aivenKafkaErrorHandler: AivenKafkaErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(simpleConsumerConfig())

        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.setCommonErrorHandler(aivenKafkaErrorHandler)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }

    @Bean
    fun schemaRegistryClient(): SchemaRegistryClient {
        return CachedSchemaRegistryClient(kafkaSchemaRegistryUrl, 20)
    }

    @Bean
    fun avroAivenKafkaListenerContainerFactory(
        schemaRegistryClient: SchemaRegistryClient,
        aivenKafkaErrorHandler: AivenKafkaErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<NokkelInput, DoneInput> {
        val consumerFactory = DefaultKafkaConsumerFactory(
            avroConsumerConfig(),
            KafkaAvroDeserializer(schemaRegistryClient),
            KafkaAvroDeserializer(schemaRegistryClient),
        )

        val factory = ConcurrentKafkaListenerContainerFactory<NokkelInput, DoneInput>()
        factory.consumerFactory = consumerFactory
        factory.setCommonErrorHandler(aivenKafkaErrorHandler)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }
}
