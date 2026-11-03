package no.nav.helse.flex.sykepengesoknad

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be equal to ignoring nano and zone`
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class SykepengesoknadMottakTest : FellesTestOppsett() {
    @Test
    fun `Mottar sykepengesøknad`() {
        sykepengesoknadRepository.deleteAll()

        val soknad =
            SykepengesoknadDTO(
                fnr = "bla",
                id = UUID.randomUUID().toString(),
                type = SoknadstypeDTO.ARBEIDSTAKERE,
                status = SoknadsstatusDTO.SENDT,
                fom = LocalDate.now().minusDays(1),
                tom = LocalDate.now(),
                sendtNav = LocalDateTime.now().minusDays(3),
                sendtArbeidsgiver = LocalDateTime.now().minusDays(6),
                startSyketilfelle = LocalDate.now().minusDays(10),
                arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
                arbeidsgiver = ArbeidsgiverDTO(navn = "Bedriften AS", orgnummer = "123456547"),
            )

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id).shouldBeNull()

        sendSykepengesoknad(soknad)

        await().atMost(10, TimeUnit.SECONDS).until {
            sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id) != null
        }

        val soknadLagret = sykepengesoknadRepository.findBySykepengesoknadUuid(soknad.id)
        soknadLagret.shouldNotBeNull()
        soknadLagret.orgnummer shouldBe soknad.arbeidsgiver!!.orgnummer!!
        soknadLagret.fom shouldBe soknad.fom!!
        soknadLagret.tom shouldBe soknad.tom!!
        soknadLagret.fnr shouldBe soknad.fnr
        soknadLagret.sendt `should be equal to ignoring nano and zone` soknad.sendtArbeidsgiver!!.tilOsloZone()
        soknadLagret.opprettetDatabase.shouldNotBeNull()
    }
}
