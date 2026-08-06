package com.haneul.medassist.service

import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionProcessingStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionRule
import com.haneul.medassist.domain.supplement.SupplementInteractionSeverity
import com.haneul.medassist.domain.supplement.toExplanationRequest
import com.haneul.medassist.repository.JsonSupplementRuleCatalog
import com.haneul.medassist.repository.SupplementInteractionRuleRepository
import com.haneul.medassist.support.FIXED_TIME
import com.haneul.medassist.support.canonicalIngredient
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.supplementSnapshot
import com.haneul.medassist.support.testCatalog
import com.haneul.medassist.support.verifiedDrugProduct
import com.haneul.medassist.support.verifiedMapping
import com.haneul.medassist.support.verifiedRule
import com.haneul.medassist.support.verifiedSource
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplementInteractionAnalysisServiceTest {
    @Test
    fun `single drug ingredient and single supplement ingredient maps CAUTION evidence`() {
        val catalog = testCatalog(rules = listOf(verifiedRule()))
        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.CAUTION, result.severity)
        assertEquals(SupplementInteractionProcessingStatus.COMPLETED, result.processingStatus)
        assertTrue(result.coverage.complete)
        assertEquals(1, result.coverage.totalPairs)
        assertEquals(1, result.coverage.evaluatedPairs)
        assertEquals("SRC-1", result.evidence.single().sourceReferenceId)
        assertEquals("검수된 공식 근거 원문", result.evidence.single().originalText)
        assertEquals(result.severity, result.evidenceBundle.immutableDecision)
        assertEquals(result.severity, result.toExplanationRequest().immutableDecision)
        assertEquals(listOf("RULE-1"), result.toExplanationRequest().matchedRules.map { it.ruleId })
        assertEquals("D-1", result.toExplanationRequest().officialDrugIngredients.single().providerCode)
        assertEquals("SRC-1", result.toExplanationRequest().evidenceTexts.single().sourceReferenceId)
        assertEquals(SupplementInteractionAnalysisService.DISCLAIMER, result.disclaimer)
    }

    @Test
    fun `AVOID rule has priority over CAUTION across multiple pairs`() {
        val sources = listOf(verifiedSource("SRC-1"), verifiedSource("SRC-2"))
        val ingredients = listOf(
            canonicalIngredient("CAN-1", "SRC-1"),
            canonicalIngredient("CAN-2", "SRC-2"),
        )
        val mappings = listOf(
            verifiedMapping("MAP-1", canonicalId = "CAN-1", sourceId = "SRC-1"),
            verifiedMapping("MAP-2", canonicalId = "CAN-2", sourceId = "SRC-2"),
        )
        val rules = listOf(
            verifiedRule("RULE-CAUTION", "D-1", "CAN-1", "SRC-1"),
            verifiedRule(
                id = "RULE-AVOID",
                drugCode = "D-2",
                canonicalId = "CAN-2",
                sourceId = "SRC-2",
                severity = SupplementInteractionSeverity.AVOID_COMBINATION,
            ),
        )
        val catalog = testCatalog(sources, ingredients, mappings, rules)
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "두번째 성분")),
        )

        val result = service(catalog, medication).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.AVOID_COMBINATION, result.severity)
        assertEquals(4, result.coverage.totalPairs)
        assertEquals(4, result.coverage.evaluatedPairs)
        assertEquals(2, result.coverage.matchedPairs)
    }

    @Test
    fun `complex drug and supplement inputs evaluate full Cartesian product`() {
        val catalog = testCatalog(
            sources = listOf(verifiedSource("SRC-1"), verifiedSource("SRC-2")),
            ingredients = listOf(
                canonicalIngredient("CAN-1", "SRC-1"),
                canonicalIngredient("CAN-2", "SRC-2"),
            ),
            mappings = listOf(
                verifiedMapping("MAP-1", canonicalId = "CAN-1", sourceId = "SRC-1"),
                verifiedMapping("MAP-2", canonicalId = "CAN-2", sourceId = "SRC-2"),
            ),
        )
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "두번째 성분")),
        )

        val result = service(catalog, medication).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND, result.severity)
        assertEquals(4, result.evaluatedPairs.size)
        assertTrue(result.coverage.complete)
        assertEquals(100, result.coverage.percentage)
        assertTrue(result.message.contains("안전하다는 의미가 아닙니다"))
    }

    @Test
    fun `missing verified mapping produces UNKNOWN and incomplete coverage`() {
        val catalog = testCatalog(mappings = emptyList())

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertFalse(result.coverage.complete)
        assertFalse(result.coverage.supplementIngredientMappingAvailable)
        assertTrue("VERIFIED_SUPPLEMENT_MAPPING_NOT_FOUND" in result.failedSteps)
    }

    @Test
    fun `DRAFT mapping is not used in production analysis`() {
        val catalog = testCatalog(
            mappings = listOf(verifiedMapping(status = EvidenceVerificationStatus.DRAFT)),
        )

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertTrue(result.supplementIngredients.isEmpty())
    }

    @Test
    fun `drug ingredient without official provider code cannot produce no-rule result`() {
        val medication = resolvedMedication(listOf(officialDrugIngredient(null)))

        val result = service(testCatalog(), medication).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertEquals(1, result.coverage.failedPairs)
        assertFalse(result.coverage.medicationIngredientsComplete)
        assertTrue("DRUG_INGREDIENT_CODE_MISSING" in result.failedSteps)
    }

    @Test
    fun `repository unavailability returns UNKNOWN instead of no verified rule`() {
        val catalog = testCatalog()
        val unavailableRules = object : SupplementInteractionRuleRepository {
            override fun findVerified(
                drugIngredientCode: String,
                supplementIngredientCanonicalId: String,
                at: Instant,
            ): List<SupplementInteractionRule> = error("not available")

            override fun isAvailable(): Boolean = false
        }

        val result = service(catalog, ruleRepository = unavailableRules).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertFalse(result.coverage.ruleRepositoryAvailable)
        assertEquals(1, result.coverage.failedPairs)
    }

    @Test
    fun `verified risk remains while another pair failure makes coverage partial`() {
        val avoid = verifiedRule(severity = SupplementInteractionSeverity.AVOID_COMBINATION)
        val catalog = testCatalog(rules = listOf(avoid))
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "두번째 성분")),
        )
        val partlyFailingRules = object : SupplementInteractionRuleRepository {
            override fun findVerified(
                drugIngredientCode: String,
                supplementIngredientCanonicalId: String,
                at: Instant,
            ): List<SupplementInteractionRule> = if (drugIngredientCode == "D-1") listOf(avoid) else error("failure")

            override fun isAvailable(): Boolean = true
        }

        val result = service(catalog, medication, ruleRepository = partlyFailingRules).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.AVOID_COMBINATION, result.severity)
        assertEquals(SupplementInteractionProcessingStatus.PARTIAL, result.processingStatus)
        assertFalse(result.coverage.complete)
        assertEquals(1, result.coverage.failedPairs)
    }

    @Test
    fun `missing medication and supplement products are explicit failed steps`() {
        val result = service(
            catalog = testCatalog(),
            medication = MedicationEvidenceProvider { MedicationEvidenceResolution.NotFound },
            supplement = SupplementProductEvidenceProvider { SupplementProductEvidenceResolution.NotFound },
        ).analyze("P-X", "S-X")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertEquals(SupplementInteractionProcessingStatus.FAILED, result.processingStatus)
        assertTrue("MEDICATION_NOT_FOUND" in result.failedSteps)
        assertTrue("SUPPLEMENT_NOT_FOUND" in result.failedSteps)
    }

    private fun service(
        catalog: JsonSupplementRuleCatalog,
        medication: MedicationEvidenceProvider = resolvedMedication(listOf(officialDrugIngredient())),
        supplement: SupplementProductEvidenceProvider = SupplementProductEvidenceProvider {
            SupplementProductEvidenceResolution.Resolved(supplementSnapshot())
        },
        ruleRepository: SupplementInteractionRuleRepository = catalog,
    ) = SupplementInteractionAnalysisService(
        medicationProvider = medication,
        supplementProvider = supplement,
        sourceRepository = catalog,
        canonicalRepository = catalog,
        mappingRepository = catalog,
        ruleRepository = ruleRepository,
    )

    private fun resolvedMedication(ingredients: List<com.haneul.medassist.domain.medication.Ingredient>) =
        MedicationEvidenceProvider {
            MedicationEvidenceResolution.Resolved(
                product = verifiedDrugProduct(),
                ingredients = ingredients,
                ingredientsComplete = ingredients.isNotEmpty(),
                overview = null,
            )
        }
}
