package no.nav.helse.flex.varseltekst

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

class VenterPaaInntektsmeldingTest {

    enum class FormaterParams(val tid: LocalDate, val formatert: String) {
        ETT_DAG_SIFFER(LocalDate.parse("2020-01-01"), "1. januar 2020"),
        TO_DAG_SIFFER(LocalDate.parse("2020-01-10"), "10. januar 2020"),
    }

    @ParameterizedTest
    @EnumSource(FormaterParams::class)
    fun `dato formateres riktig`(param: FormaterParams) {
        param.tid.formater() `should be equal to` param.formatert
    }

}
