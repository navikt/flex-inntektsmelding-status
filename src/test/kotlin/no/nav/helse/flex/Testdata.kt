package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.*
import java.time.LocalDate
import java.util.*

object Testdata {
    val fnr = "12345678901"
    val vedtaksperiodeId = UUID.randomUUID().toString()
    val behandlingId = UUID.randomUUID().toString()
    val soknadId = UUID.randomUUID().toString()
    val orgNr = "123456547"
    val fom = LocalDate.of(2022, 5, 29)
    val tom = LocalDate.of(2022, 6, 30)
    val soknad =
        SykepengesoknadDTO(
            fnr = fnr,
            id = soknadId,
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            startSyketilfelle = fom,
            fom = fom,
            tom = tom,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
        )
}
