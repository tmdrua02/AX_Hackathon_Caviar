package com.haneul.medassist.service

import com.haneul.medassist.domain.evidence.EvidenceVerificationStatus
import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import com.haneul.medassist.domain.supplement.SupplementInteractionProcessingStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
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
import tools.jackson.databind.json.JsonMapper
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
        assertEquals("SRC-1", result.toExplanationRequest().evidence.single().sourceReferenceId)
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
    fun `two drug ingredients and three verified supplement ingredients evaluate six unique pairs`() {
        val sources = (1..3).map { verifiedSource("SRC-$it") }
        val ingredients = (1..3).map { canonicalIngredient("CAN-$it", "SRC-$it") }
        val mappings = (1..3).map {
            verifiedMapping("MAP-$it", canonicalId = "CAN-$it", sourceId = "SRC-$it")
        }
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "TEST_DRUG_INGREDIENT_B")),
        )

        val result = service(testCatalog(sources, ingredients, mappings), medication).analyze("P-1", "S-1")

        assertEquals(6, result.coverage.totalPairs)
        assertEquals(6, result.coverage.evaluatedPairs)
        assertEquals(0, result.coverage.failedPairs)
        assertEquals(6, result.evaluatedPairs.distinctBy {
            it.drugIngredientCode to it.supplementIngredientCanonicalId
        }.size)
        assertEquals(SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND, result.severity)
    }

    @Test
    fun `missing verified mapping produces UNKNOWN and incomplete coverage`() {
        val catalog = testCatalog(mappings = emptyList())

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertFalse(result.coverage.complete)
        assertFalse(result.coverage.supplementIngredientMappingAvailable)
        assertTrue(SupplementInteractionFailureCode.SUPPLEMENT_INGREDIENT_MAPPING_MISSING in result.failedSteps)
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
        assertTrue(SupplementInteractionFailureCode.MEDICATION_INGREDIENT_CODE_MISSING in result.failedSteps)
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
    fun `catalog unavailability is explicit and produces UNKNOWN`() {
        val unavailable = SupplementRuleCatalogAuditMetadata(
            available = false,
            verified = false,
            catalogVersion = null,
            schemaVersion = null,
            catalogChecksum = null,
            loadedAt = FIXED_TIME,
            sourceCount = 0,
            canonicalIngredientCount = 0,
            productMappingCount = 0,
            interactionRuleCount = 0,
            validationErrorCodes = listOf("CATALOG_NOT_FOUND"),
        )
        val catalog = JsonSupplementRuleCatalog.unavailable(unavailable)

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertTrue(SupplementInteractionFailureCode.RULE_CATALOG_UNAVAILABLE in result.failedSteps)
        assertFalse(result.catalogMetadata.available)
    }

    @Test
    fun `unverified catalog manifest cannot contribute production risk decisions`() {
        val metadata = SupplementRuleCatalogAuditMetadata(
            available = true,
            verified = false,
            catalogVersion = "TEST-DRAFT-CATALOG",
            schemaVersion = "1.0",
            catalogChecksum = "d".repeat(64),
            loadedAt = FIXED_TIME,
            sourceCount = 1,
            canonicalIngredientCount = 1,
            productMappingCount = 1,
            interactionRuleCount = 1,
            validationErrorCodes = emptyList(),
        )
        val catalog = testCatalog(rules = listOf(verifiedRule()), metadata = metadata)

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertTrue(SupplementInteractionFailureCode.RULE_CATALOG_INVALID in result.failedSteps)
        assertTrue(result.matchedRules.isEmpty())
    }

    @Test
    fun `repository response containing DRAFT rule is rejected by deterministic engine`() {
        val catalog = testCatalog()
        val draftRule = verifiedRule(status = EvidenceVerificationStatus.DRAFT)
        val unsafeRepository = object : SupplementInteractionRuleRepository {
            override fun findVerified(
                drugIngredientCode: String,
                supplementIngredientCanonicalId: String,
                at: Instant,
            ) = listOf(draftRule)

            override fun isAvailable() = true
        }

        val result = service(catalog, ruleRepository = unsafeRepository).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertTrue(result.matchedRules.isEmpty())
        assertTrue(SupplementInteractionFailureCode.RULE_CATALOG_INVALID in result.failedSteps)
        assertFalse(result.coverage.complete)
    }

    @Test
    fun `catalog version and checksum survive result bundle and immutable LLM request`() {
        val metadata = SupplementRuleCatalogAuditMetadata(
            available = true,
            verified = true,
            catalogVersion = "catalog-test-v1",
            schemaVersion = "1.0",
            catalogChecksum = "c".repeat(64),
            loadedAt = FIXED_TIME,
            sourceCount = 1,
            canonicalIngredientCount = 1,
            productMappingCount = 1,
            interactionRuleCount = 1,
            validationErrorCodes = emptyList(),
        )
        val catalog = testCatalog(
            sources = listOf(verifiedSource().copy(sourceVersion = "source-test-v1")),
            rules = listOf(verifiedRule().copy(ruleVersion = "rule-test-v1")),
            metadata = metadata,
        )

        val result = service(catalog).analyze("P-1", "S-1")

        assertEquals(metadata, result.catalogMetadata)
        assertEquals(metadata, result.evidenceBundle.catalogMetadata)
        assertEquals(metadata, result.toExplanationRequest().catalogMetadata)
        assertEquals("rule-test-v1", result.toExplanationRequest().matchedRules.single().ruleVersion)
        assertEquals("source-test-v1", result.toExplanationRequest().evidence.single().sourceVersion)
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
    fun `verified CAUTION remains while another pair failure makes coverage partial`() {
        val caution = verifiedRule(severity = SupplementInteractionSeverity.CAUTION)
        val catalog = testCatalog(rules = listOf(caution))
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "TEST_DRUG_INGREDIENT_B")),
        )
        val partlyFailingRules = object : SupplementInteractionRuleRepository {
            override fun findVerified(
                drugIngredientCode: String,
                supplementIngredientCanonicalId: String,
                at: Instant,
            ): List<SupplementInteractionRule> = if (drugIngredientCode == "D-1") listOf(caution) else error("failure")

            override fun isAvailable(): Boolean = true
        }

        val result = service(catalog, medication, ruleRepository = partlyFailingRules).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.CAUTION, result.severity)
        assertFalse(result.coverage.complete)
        assertEquals(1, result.coverage.failedPairs)
        assertTrue(SupplementInteractionFailureCode.RULE_LOOKUP_FAILED in result.failedSteps)
        assertTrue(SupplementInteractionFailureCode.PAIR_EVALUATION_INCOMPLETE in result.failedSteps)
    }

    @Test
    fun `no matching rule plus one pair failure is UNKNOWN rather than no-rule`() {
        val catalog = testCatalog()
        val medication = resolvedMedication(
            listOf(officialDrugIngredient("D-1"), officialDrugIngredient("D-2", "TEST_DRUG_INGREDIENT_B")),
        )
        val partlyFailingRules = object : SupplementInteractionRuleRepository {
            override fun findVerified(
                drugIngredientCode: String,
                supplementIngredientCanonicalId: String,
                at: Instant,
            ): List<SupplementInteractionRule> = if (drugIngredientCode == "D-1") emptyList() else error("failure")

            override fun isAvailable(): Boolean = true
        }

        val result = service(catalog, medication, ruleRepository = partlyFailingRules).analyze("P-1", "S-1")

        assertEquals(SupplementInteractionSeverity.UNKNOWN, result.severity)
        assertFalse(result.coverage.complete)
    }

    @Test
    fun `LLM explanation request serializes immutable identifiers evidence and catalog audit metadata`() {
        val metadata = SupplementRuleCatalogAuditMetadata(
            available = true,
            verified = true,
            catalogVersion = "TEST-CATALOG-V1",
            schemaVersion = "1.0",
            catalogChecksum = "a".repeat(64),
            loadedAt = FIXED_TIME,
            sourceCount = 1,
            canonicalIngredientCount = 1,
            productMappingCount = 1,
            interactionRuleCount = 1,
            validationErrorCodes = emptyList(),
        )
        val catalog = testCatalog(rules = listOf(verifiedRule()), metadata = metadata)
        val request = service(catalog).analyze("P-1", "S-1").toExplanationRequest()

        val json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(request)

        assertTrue(json.contains("\"immutableDecision\":\"CAUTION\""))
        assertTrue(json.contains("\"productCode\":\"P-1\""))
        assertTrue(json.contains("\"statementNo\":\"S-1\""))
        assertTrue(json.contains("\"providerCode\":\"D-1\""))
        assertTrue(json.contains("\"canonicalId\":\"CAN-1\""))
        assertTrue(json.contains("\"ruleId\":\"RULE-1\""))
        assertTrue(json.contains("\"sourceReferenceId\":\"SRC-1\""))
        assertTrue(json.contains("검수된 공식 근거 원문"))
        assertTrue(json.contains("\"catalogChecksum\":\"${"a".repeat(64)}\""))
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
        assertTrue(SupplementInteractionFailureCode.MEDICATION_NOT_FOUND in result.failedSteps)
        assertTrue(SupplementInteractionFailureCode.SUPPLEMENT_NOT_FOUND in result.failedSteps)
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
