//package no.nav.helse.flex.vedtak
//
//import org.springframework.data.annotation.Id
//import org.springframework.data.relational.core.mapping.Table
//import org.springframework.data.repository.CrudRepository
//import org.springframework.stereotype.Repository
//import java.time.Instant
//import java.time.LocalDate
//import java.util.*
//
//@Repository
//interface VedtakRepository : CrudRepository<Vedtak, String> {
//    fun findByVedtakUuid(VedtakUuid: String): Vedtak?
//}
//
//@Table("inntektsmelding")
//data class Vedtak(
//    @Id
//    val id: String? = null,
//    val VedtakUuid: String,
//    val orgnummer: String?,
//    val soknadstype: String,
//    val fom: LocalDate,
//    val tom: LocalDate,
//    val fnr: String,
//    val sendt: Instant,
//    val opprettetDatabase: Instant,
//)
