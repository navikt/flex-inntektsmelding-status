package no.nav.helse.flex.inntektsmelding

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface InntektsmeldingRepository : CrudRepository<InntektsmeldingDbRecord, String> {
    fun existsByInntektsmeldingId(innteksmeldingId: String): Boolean

    fun findByFnrIn(fnrs: List<String>): List<InntektsmeldingDbRecord>
}
