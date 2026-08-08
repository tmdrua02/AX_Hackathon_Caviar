package com.haneul.medassist.service

import com.haneul.medassist.domain.supplement.SupplementInteractionPresentationResult
import com.haneul.medassist.domain.supplement.toExplanationRequest
import org.springframework.stereotype.Service

@Service
class SupplementInteractionPresentationService(
    private val analysisService: SupplementInteractionAnalysisService,
    private val explanationService: SupplementInteractionExplanationService,
) {
    fun analyzeAndExplain(
        medicationProductCode: String,
        supplementStatementNo: String,
    ): SupplementInteractionPresentationResult {
        val analysis = analysisService.analyze(medicationProductCode, supplementStatementNo)
        val immutableRequest = analysis.toExplanationRequest()
        val explanation = explanationService.explain(immutableRequest)
        return SupplementInteractionPresentationResult(analysis, explanation)
    }
}
