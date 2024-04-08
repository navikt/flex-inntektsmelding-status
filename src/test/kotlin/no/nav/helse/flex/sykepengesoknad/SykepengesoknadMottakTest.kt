package no.nav.helse.flex.sykepengesoknad

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.`should be equal to ignoring nano and zone`
import no.nav.helse.flex.sykepengesoknad.kafka.*
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
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
        soknadLagret.orgnummer `should be equal to` soknad.arbeidsgiver!!.orgnummer!!
        soknadLagret.fom `should be equal to` soknad.fom!!
        soknadLagret.tom `should be equal to` soknad.tom!!
        soknadLagret.fnr `should be equal to` soknad.fnr
        soknadLagret.sendt `should be equal to ignoring nano and zone` soknad.sendtArbeidsgiver!!.tilOsloZone()
        soknadLagret.opprettetDatabase.shouldNotBeNull()
    }
}
