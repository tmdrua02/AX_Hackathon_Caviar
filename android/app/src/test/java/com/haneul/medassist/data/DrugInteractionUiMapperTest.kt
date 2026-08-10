package com.haneul.medassist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugInteractionUiMapperTest {
    private val added = medication("new", "NEW-1", "새 약", ProductType.OTC_DRUG)
    private val existing = medication("old", "OLD-1", "기존 약", ProductType.PRESCRIPTION_DRUG)

    @Test
    fun `official duplicate result maps without demo evidence`() {
        val response = response(
            DrugInteractionPairResponse(
                requestedExistingProductCode = "OLD-1",
                processingStatus = "COMPLETED",
                severity = Severity.DUPLICATE_OR_SIMILAR,
                summary = "동일한 공식 성분코드가 확인되었습니다.",
                evidence = listOf(
                    DrugInteractionEvidenceResponse(
                        ingredientA = "성분 A",
                        ingredientB = "성분 A",
                        evidenceType = "DUPLICATE",
                        sourceName = "식품의약품안전처",
                        sourceUrl = "https://example.test/official",
                        sourceRecordId = "OFFICIAL-1",
                        retrievedAt = "2026-08-10T00:00:00Z",
                        originalSummary = "동일 공식 성분코드",
                        sourceType = "PUBLIC_DATA",
                    ),
                ),
                coverage = DrugInteractionCoverageResponse(2, 2, 1, 1, 0, 100, true),
                analyzedAt = "2026-08-10T00:00:00Z",
            ),
        )

        val mapped = DrugInteractionUiMapper.map(response, added, listOf(existing))

        assertEquals(Severity.DUPLICATE_OR_SIMILAR, mapped.results.single().severity)
        assertEquals("OFFICIAL-1", mapped.results.single().evidence.single().sourceRecordId)
        assertFalse(mapped.results.single().evidence.single().sourceRecordId.orEmpty().startsWith("MOCK"))
        assertEquals(1, mapped.coverage.successfulQueries)
    }

    @Test
    fun `missing product code remains unknown and is counted as unidentified`() {
        val unresolved = existing.copy(productCode = null)

        val mapped = DrugInteractionUiMapper.map(null, added, listOf(unresolved))

        assertEquals(Severity.UNKNOWN, mapped.results.single().severity)
        assertTrue(mapped.results.single().easyExplanation.contains("품목기준코드"))
        assertEquals(1, mapped.coverage.unidentifiedIngredients)
    }

    @Test
    fun `health supplement is routed away from drug interaction analysis`() {
        val supplement = existing.copy(productType = ProductType.HEALTH_SUPPLEMENT)

        val mapped = DrugInteractionUiMapper.map(null, added, listOf(supplement))

        assertEquals(Severity.UNKNOWN, mapped.results.single().severity)
        assertTrue(mapped.results.single().easyExplanation.contains("약–건강기능식품"))
    }

    private fun response(pair: DrugInteractionPairResponse) = DrugInteractionBatchResponse(
        processingStatus = "COMPLETED",
        newMedicationProductCode = "NEW-1",
        results = listOf(pair),
        coverage = DrugInteractionBatchCoverage(1, 1, 0, 0, 1, 1, 0),
        analyzedAt = "2026-08-10T00:00:00Z",
        disclaimer = "전문가와 상담하세요.",
    )

    private fun medication(id: String, code: String?, name: String, type: ProductType) = Medication(
        id = id,
        name = name,
        productType = type,
        productCode = code,
        ingredients = listOf(Ingredient("성분 A", "ingredient-a", "ING-A")),
    )
}
