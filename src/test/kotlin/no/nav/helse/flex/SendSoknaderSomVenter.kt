package no.nav.helse.flex

import no.nav.helse.flex.Testdata.fom
import no.nav.helse.flex.Testdata.orgNr
import no.nav.helse.flex.Testdata.tom
import no.nav.helse.flex.sykepengesoknad.kafka.*
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatustype
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi
import no.nav.helse.flex.vedtaksperiodebehandling.StatusVerdi.VENTER_PÅ_ARBEIDSGIVER
import org.awaitility.Awaitility.await
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

fun FellesTestOppsett.sendSoknaderSomVenterPaArbeidsgiver(index: Int) {
    val soknaden =
        SykepengesoknadDTO(
            fnr = (10000000000 + index).toString(),
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            startSyketilfelle = fom,
            fom = fom,
            tom = tom,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
        )
    sendSoknad(soknaden)
    sendSoknad(
        soknaden.copy(
            status = SoknadsstatusDTO.SENDT,
            sendtNav = LocalDateTime.now(),
        ),
    )

    await().atMost(5, TimeUnit.SECONDS).until {
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id) != null
    }

    val tidspunkt = OffsetDateTime.now()
    val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = UUID.randomUUID().toString(),
            behandlingId = UUID.randomUUID().toString(),
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = tidspunkt,
            eksterneSøknadIder = listOf(soknaden.id),
        )
    sendBehandlingsstatusMelding(behandlingstatusmelding)

    sendBehandlingsstatusMelding(
        behandlingstatusmelding.copy(
            status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
        ),
    )

    awaitOppdatertStatus(
        VENTER_PÅ_ARBEIDSGIVER,
        behandlingId = behandlingstatusmelding.behandlingId,
        vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
    )
}

fun FellesTestOppsett.sendSoknaderSomVenterPaSaksbehandler(index: Int) {
    val soknaden =
        SykepengesoknadDTO(
            fnr = (10000000000 + index).toString(),
            id = UUID.randomUUID().toString(),
            type = SoknadstypeDTO.ARBEIDSTAKERE,
            status = SoknadsstatusDTO.NY,
            startSyketilfelle = fom,
            fom = fom,
            tom = tom,
            arbeidssituasjon = ArbeidssituasjonDTO.ARBEIDSTAKER,
            arbeidsgiver = ArbeidsgiverDTO(navn = "Flex AS", orgnummer = orgNr),
        )
    sendSoknad(soknaden)
    sendSoknad(
        soknaden.copy(
            status = SoknadsstatusDTO.SENDT,
            sendtNav = LocalDateTime.now(),
        ),
    )

    await().atMost(5, TimeUnit.SECONDS).until {
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id) != null
    }

    val tidspunkt = OffsetDateTime.now()
    val behandlingstatusmelding =
        Behandlingstatusmelding(
            vedtaksperiodeId = UUID.randomUUID().toString(),
            behandlingId = UUID.randomUUID().toString(),
            status = Behandlingstatustype.OPPRETTET,
            tidspunkt = tidspunkt,
            eksterneSøknadIder = listOf(soknaden.id),
        )

    sendInntektsmelding(
        skapInntektsmelding(
            fnr = soknaden.fnr,
            virksomhetsnummer = soknaden.arbeidsgiver!!.orgnummer,
            refusjonBelopPerMnd = BigDecimal(5000),
            beregnetInntekt = BigDecimal(10000),
            vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
        ),
    )

    sendBehandlingsstatusMelding(behandlingstatusmelding)

    sendBehandlingsstatusMelding(
        behandlingstatusmelding.copy(
            status = Behandlingstatustype.VENTER_PÅ_ARBEIDSGIVER,
        ),
    )
    sendBehandlingsstatusMelding(
        behandlingstatusmelding.copy(
            status = Behandlingstatustype.VENTER_PÅ_SAKSBEHANDLER,
        ),
    )

    awaitOppdatertStatus(
        StatusVerdi.VENTER_PÅ_SAKSBEHANDLER,
        behandlingId = behandlingstatusmelding.behandlingId,
        vedtaksperiodeId = behandlingstatusmelding.vedtaksperiodeId,
    )
}
