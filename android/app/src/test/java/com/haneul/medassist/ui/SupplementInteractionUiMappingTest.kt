package com.haneul.medassist.ui

import com.haneul.medassist.data.SupplementInteractionSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementInteractionUiMappingTest {
    @Test
    fun avoidAndCautionUseEvidenceBoundedLanguage() {
        val avoid = supplementSeverityUi(SupplementInteractionSeverity.AVOID_COMBINATION).guidance
        val caution = supplementSeverityUi(SupplementInteractionSeverity.CAUTION).guidance

        assertTrue(avoid.contains("근거"))
        assertFalse(avoid.contains("절대 복용 금지"))
        assertTrue(caution.contains("주의"))
    }

    @Test
    fun noVerifiedRuleFoundAlwaysIncludesSafetyCaveat() {
        val guidance = supplementSeverityUi(SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND).guidance

        assertTrue(guidance.contains("안전하다는 의미는 아닙니다"))
    }

    @Test
    fun unknownShowsIncompleteDataInsteadOfTransportError() {
        val presentation = supplementSeverityUi(SupplementInteractionSeverity.UNKNOWN)

        assertTrue(presentation.title.contains("판단할 수 없음"))
        assertTrue(presentation.guidance.contains("데이터"))
    }

    @Test
    fun stableFailureCodesMapToUserMessagesAndFutureCodesHaveFallback() {
        assertTrue(supplementFailureMessage("SUPPLEMENT_INGREDIENT_MAPPING_MISSING").contains("원료 정보"))
        assertTrue(supplementFailureMessage("RULE_CATALOG_UNAVAILABLE").contains("데이터베이스"))
        assertTrue(supplementFailureMessage("PAIR_EVALUATION_INCOMPLETE").contains("일부 성분 조합"))
        assertTrue(supplementFailureMessage("FUTURE_CODE").contains("일부 정보"))
    }
}
