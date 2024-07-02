package no.nav.helse.flex

import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.inntektsmeldingkontrakt.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.*

object Testdata {
    val fnr = "12345678901"
    val vedtaksperiodeId = "49dde947-3be4-40a5-a62b-ca76da43db16"
    val behandlingId = "5e914896-967b-4218-b186-39a6dfacdab9"
    val soknadId = "cba55aa7-8d6d-4264-9a3b-f40ba2e3a146"
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

val foersteJanuar = LocalDate.of(2019, 1, 1)


fun skapInntektsmelding(
    fnr: String,
    virksomhetsnummer: String?,
    refusjonBelopPerMnd: BigDecimal?,
    beregnetInntekt: BigDecimal?,
    vedtaksperiodeId: String? = null,
    foersteFravaersdag: LocalDate = foersteJanuar
): Inntektsmelding {
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
        foersteFravaersdag = foersteFravaersdag,
        naerRelasjon = true,
        avsenderSystem = AvsenderSystem("AltinnPortal", "1.0"),
        innsenderFulltNavn = "",
        innsenderTelefon = "",
        virksomhetsnummer = virksomhetsnummer,
        vedtaksperiodeId =
            if (vedtaksperiodeId != null) {
                UUID.fromString(vedtaksperiodeId)
            } else {
                null
            },
    )
}
