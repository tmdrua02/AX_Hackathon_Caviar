package com.haneul.medassist.service

import com.haneul.medassist.client.llm.SupplementInteractionExplanationClient
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.supplementSnapshot
import com.haneul.medassist.support.testCatalog
import com.haneul.medassist.support.verifiedDrugProduct
import com.haneul.medassist.support.verifiedRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupplementInteractionPresentationServiceTest {
    @Test
    fun `orchestrator sends only immutable explanation request after deterministic analysis`() {
        var captured: SupplementInteractionExplanationRequest? = null
        val client = fakeClient { request ->
            captured = request
            GeneratedSupplementInteractionExplanation(
                "근거 기반 설명",
                "제공된 원문만 요약",
                "의사 또는 약사와 상담하세요.",
            )
        }
        val result = presentation(analysis(testCatalog(rules = listOf(verifiedRule()))), client)
            .analyzeAndExplain("P-1", "S-1")
        val request = assertNotNull(captured)

        assertEquals(SupplementInteractionSeverity.CAUTION, result.analysis.severity)
        assertEquals(result.analysis.severity, request.immutableDecision)
        assertEquals(result.analysis.medication?.productCode, request.medication?.productCode)
        assertEquals(result.analysis.supplement?.statementNo, request.supplement?.statementNo)
        assertEquals(result.analysis.drugIngredients.single().providerCode, request.officialDrugIngredients.single().providerCode)
        assertEquals(result.analysis.supplementIngredients.single().id, request.verifiedSupplementIngredients.single().canonicalId)
        assertEquals(result.analysis.matchedRules.single().id, request.matchedRules.single().ruleId)
        assertEquals(result.analysis.evidence.single().sourceReferenceId, request.evidence.single().sourceReferenceId)
        assertEquals(result.analysis.coverage, request.coverage)
        assertEquals(result.analysis.failedSteps, request.failedSteps)
        assertEquals(result.analysis.catalogMetadata, request.catalogMetadata)
        assertEquals(SupplementInteractionExplanationStatus.GENERATED, result.explanation.status)
    }

    @Test
    fun `empty production-like catalog stays UNKNOWN even when LLM claims safety`() {
        val emptyCatalog = testCatalog(sources = emptyList(), ingredients = emptyList(), mappings = emptyList())
        val client = fakeClient {
            GeneratedSupplementInteractionExplanation(
                "함께 복용해도 괜찮습니다",
                "문제없습니다",
                "복용 가능합니다",
            )
        }

        val result = presentation(analysis(emptyCatalog), client).analyzeAndExplain("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.analysis.severity)
        assertTrue(SupplementInteractionFailureCode.SUPPLEMENT_INGREDIENT_MAPPING_MISSING in result.analysis.failedSteps)
        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, result.explanation.status)
        assertTrue(result.explanation.summary.contains("충분히 확인할 수 없습니다"))
    }

    private fun analysis(catalog: com.haneul.medassist.repository.JsonSupplementRuleCatalog) =
        SupplementInteractionAnalysisService(
            medicationProvider = MedicationEvidenceProvider {
                MedicationEvidenceResolution.Resolved(
                    product = verifiedDrugProduct(),
                    ingredients = listOf(officialDrugIngredient()),
                    ingredientsComplete = true,
                    overview = null,
                )
            },
            supplementProvider = SupplementProductEvidenceProvider {
                SupplementProductEvidenceResolution.Resolved(supplementSnapshot())
            },
            sourceRepository = catalog,
            canonicalRepository = catalog,
            mappingRepository = catalog,
            ruleRepository = catalog,
        )

    private fun presentation(
        analysisService: SupplementInteractionAnalysisService,
        client: SupplementInteractionExplanationClient,
    ) = SupplementInteractionPresentationService(
        analysisService,
        SupplementInteractionExplanationService(client),
    )

    private fun fakeClient(
        answer: (SupplementInteractionExplanationRequest) -> GeneratedSupplementInteractionExplanation,
    ) = object : SupplementInteractionExplanationClient {
        override val provider = "OPENAI"
        override val model = "TEST_MODEL"
        override fun isConfigured() = true
        override fun generate(request: SupplementInteractionExplanationRequest) = answer(request)
    }
}
