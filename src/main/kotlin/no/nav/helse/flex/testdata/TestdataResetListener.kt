package no.nav.helse.flex.testdata

import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("test", "testdatareset")
class TestdataResetListener(
    private val testdataResetService: TestdataResetService,
) {
    private val log = logger()

    @KafkaListener(
        topics = [TESTDATA_RESET_TOPIC],
        id = "flex-inntektsmelding-status-testdatareset-v1",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        testdataResetService.slettTestdata(cr.value())
        acknowledgment.acknowledge()
    }
}

const val TESTDATA_RESET_TOPIC = "flex.testdata-reset"
