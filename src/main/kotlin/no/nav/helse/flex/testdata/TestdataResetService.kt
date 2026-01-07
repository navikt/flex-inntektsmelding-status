package no.nav.helse.flex.testdata

import no.nav.helse.flex.inntektsmelding.InntektsmeldingRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.SykepengesoknadRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingStatusRepository
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingSykepengesoknadRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
@Profile("test", "testdatareset")
class TestdataResetService(
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val inntektsmeldingRepository: InntektsmeldingRepository,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    private val vedtaksperiodeBehandlingStatusRepository: VedtaksperiodeBehandlingStatusRepository,
    private val vedtaksperiodeBehandlingSykepengesoknadRepository: VedtaksperiodeBehandlingSykepengesoknadRepository,
) {
    private val log = logger()

    fun slettTestdata(fnr: String) {
        val inntektsmeldingerSlettet = slettInntektsmeldinger(fnr)
        val vedtaksperioderSlettet = slettVedtaksperioder(fnr)

        logSletting(fnr, inntektsmeldingerSlettet, vedtaksperioderSlettet)
    }

    private fun slettInntektsmeldinger(fnr: String): Int =
        inntektsmeldingRepository.findByFnr(fnr).let {
            inntektsmeldingRepository.deleteByFnr(fnr)
            it.size
        }

    private fun slettVedtaksperioder(fnr: String): VedtaksperioderSomSkalSlettes =
        finnVedtaksperioderSomSkalSlettes(fnr).also {
            vedtaksperiodeBehandlingStatusRepository.deleteAllById(it.behandlingStatusIdListe)
            vedtaksperiodeBehandlingSykepengesoknadRepository.deleteAllById(it.vedtaksperiodeSoknadKoblingIdListe)
            vedtaksperiodeBehandlingRepository.deleteAllById(it.vedtaksperiodeBehandlingerIdListe)
            sykepengesoknadRepository.deleteAllById(it.soknadUuidListe)
        }

    fun finnVedtaksperioderSomSkalSlettes(fnr: String): VedtaksperioderSomSkalSlettes {
        val soknader = sykepengesoknadRepository.findByFnr(fnr)
        val soknaderOgVedtaksperiodeKoblinger =
            vedtaksperiodeBehandlingSykepengesoknadRepository.findBySykepengesoknadUuidIn(
                soknader.map { it.sykepengesoknadUuid },
            )

        return VedtaksperioderSomSkalSlettes(
            soknadUuidListe = soknader.map { it.id!! },
            vedtaksperiodeSoknadKoblingIdListe = soknaderOgVedtaksperiodeKoblinger.map { it.id!! },
            vedtaksperiodeBehandlingerIdListe =
                vedtaksperiodeBehandlingRepository
                    .findByIdIn(
                        soknaderOgVedtaksperiodeKoblinger.map { it.vedtaksperiodeBehandlingId },
                    ).map { it.id!! },
            behandlingStatusIdListe =
                vedtaksperiodeBehandlingStatusRepository
                    .findByVedtaksperiodeBehandlingIdIn(
                        soknaderOgVedtaksperiodeKoblinger.map {
                            it.vedtaksperiodeBehandlingId
                        },
                    ).map { it.id!! },
        )
    }

    private fun logSletting(
        fnr: String,
        inntektsmeldingerSlettet: Int,
        vedtaksperioderSlettet: VedtaksperioderSomSkalSlettes,
    ) {
        log.info(
            StringBuilder("Slettet testdata for fnr: $fnr. ")
                .append(inntektsmeldingerLog(inntektsmeldingerSlettet))
                .append(behandlingStatusLog(vedtaksperioderSlettet.behandlingStatusIdListe.size))
                .append(vedtaksperiodeBehandlingLog(vedtaksperioderSlettet.vedtaksperiodeBehandlingerIdListe.size))
                .append(koblingerLog(vedtaksperioderSlettet.vedtaksperiodeSoknadKoblingIdListe.size))
                .append(soknaderLog(vedtaksperioderSlettet.soknadUuidListe.size))
                .toString(),
        )
    }

    private fun inntektsmeldingerLog(count: Int) = "Antall: $count ${count.plural("inntektsmelding")}, "

    private fun behandlingStatusLog(count: Int) = "$count ${count.plural("vedtaksperiodebehandling status")}, "

    private fun vedtaksperiodeBehandlingLog(count: Int) = "$count ${count.plural("vedtaksperiodebehandling")}, "

    private fun koblingerLog(count: Int) = "$count ${count.plural("kobling")} mellom søknader og vedtaksperiodebehandlinger, "

    private fun soknaderLog(count: Int) = "$count ${count.plural("søknad")}."

    private fun Int.plural(word: String): String = if (this == 1) word else "${word}er"
}

data class VedtaksperioderSomSkalSlettes(
    val soknadUuidListe: List<String>,
    val vedtaksperiodeSoknadKoblingIdListe: List<String>,
    val vedtaksperiodeBehandlingerIdListe: List<String>,
    val behandlingStatusIdListe: List<String>,
)
