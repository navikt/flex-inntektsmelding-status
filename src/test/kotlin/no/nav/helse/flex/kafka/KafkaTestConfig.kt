package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.input.BeskjedInput
import no.nav.brukernotifikasjon.schemas.input.DoneInput
import no.nav.brukernotifikasjon.schemas.input.NokkelInput
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import java.io.Serializable

@Configuration
@Profile("test")
class KafkaTestConfig(
    @Value("\${KAFKA_BROKERS}") private val kafkaBootstrapServers: String
) {
    private fun commonConfig(): Map<String, String> {
        return mapOf(
            CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT"
        )
    }

    private fun producerConfig(): Map<String, Serializable> {
        return mapOf(
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to "true",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
            ProducerConfig.MAX_BLOCK_MS_CONFIG to "15000",
            ProducerConfig.RETRIES_CONFIG to "100000"
        ) + commonConfig()
    }

    fun testConsumerProps(groupId: String) = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav"
    ) + commonConfig()

    private fun avroTestProducerConfig(): Map<String, Serializable> {
        return mapOf(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://whatever.nav",
            SaslConfigs.SASL_MECHANISM to "PLAIN"
        ) + producerConfig()
    }

    fun kafkaAvroDeserializer(): KafkaAvroDeserializer {
        val config = mapOf(
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true,
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to "http://ikke.i.bruk.nav"
        )
        return KafkaAvroDeserializer(mockSchemaRegistryClient(), config)
    }

    @Bean
    fun mockSchemaRegistryClient() = MockSchemaRegistryClient()

    @Bean
    fun beskjedKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<NokkelInput, BeskjedInput> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(
            avroTestProducerConfig(),
            kafkaAvroSerializer as Serializer<NokkelInput>,
            kafkaAvroSerializer as Serializer<BeskjedInput>
        ).createProducer()
    }

    @Bean
    fun doneKafkaProducer(mockSchemaRegistryClient: MockSchemaRegistryClient): Producer<NokkelInput, DoneInput> {
        val kafkaAvroSerializer = KafkaAvroSerializer(mockSchemaRegistryClient)
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaProducerFactory(
            avroTestProducerConfig(),
            kafkaAvroSerializer as Serializer<NokkelInput>,
            kafkaAvroSerializer as Serializer<DoneInput>
        ).createProducer()
    }

    @Bean
    fun meldingProducer() = KafkaProducer<String, String>(
        producerConfig() + listOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java
        )
    )

    @Bean
    fun doneKafkaConsumer(): Consumer<GenericRecord, GenericRecord> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            testConsumerProps("done-consumer"),
            kafkaAvroDeserializer() as Deserializer<GenericRecord>,
            kafkaAvroDeserializer() as Deserializer<GenericRecord>
        ).createConsumer()
    }

    @Bean
    fun beskjedKafkaConsumer(): Consumer<GenericRecord, GenericRecord> {
        @Suppress("UNCHECKED_CAST")
        return DefaultKafkaConsumerFactory(
            testConsumerProps("beskjed-consumer"),
            kafkaAvroDeserializer() as Deserializer<GenericRecord>,
            kafkaAvroDeserializer() as Deserializer<GenericRecord>
        ).createConsumer()
    }

    @Bean
    fun meldingKafkaConsumer(): Consumer<String, String> {
        return DefaultKafkaConsumerFactory(
            testConsumerProps("melding-consumer"),
            StringDeserializer(),
            StringDeserializer()
        ).createConsumer()
    }
}
