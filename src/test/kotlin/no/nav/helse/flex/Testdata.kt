package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.inntektsmeldingkontrakt.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
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

fun skapInntektsmelding(
    fnr: String,
    virksomhetsnummer: String?,
    refusjonBelopPerMnd: BigDecimal?,
    beregnetInntekt: BigDecimal?,
    vedtaksperiodeId: UUID? = null,
): Inntektsmelding {
    val foersteJanuar = LocalDate.of(2019, 1, 1)
    val andreJanuar = LocalDate.of(2019, 1, 2)
    return Inntektsmelding(
        inntektsmeldingId = UUID.randomUUID().toString(),
        arbeidstakerFnr = fnr,
        arbeidstakerAktorId = "00000000000",
        refusjon = Refusjon(beloepPrMnd = refusjonBelopPerMnd),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        status = Status.GYLDIG,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsgiverperioder = listOf(Periode(foersteJanuar, andreJanuar)),
        beregnetInntekt = beregnetInntekt,
        inntektsdato = LocalDate.of(2023, Month.OCTOBER, 13),
        arkivreferanse = "AR123",
        ferieperioder = emptyList(),
        mottattDato = foersteJanuar.atStartOfDay(),
        foersteFravaersdag = foersteJanuar,
        naerRelasjon = true,
        avsenderSystem = AvsenderSystem("AltinnPortal", "1.0"),
        innsenderFulltNavn = "",
        innsenderTelefon = "",
        virksomhetsnummer = virksomhetsnummer,
        vedtaksperiodeId = vedtaksperiodeId,
    )
}
