package com.haneul.medassist.service

import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.client.dur.DurLookupResult
import com.haneul.medassist.domain.interaction.Evidence
import com.haneul.medassist.domain.interaction.InteractionSeverity
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.SourceMetadata
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IngredientComparisonServiceTest {
    @Test
    fun `same official code is duplicate even when DUR schema is unavailable`() {
        val service = service { _, _ -> DurLookupResult.Failure("not-called") }

        val result = service.compare(listOf(ingredient("A")), listOf(ingredient("A")))

        assertEquals(InteractionSeverity.DUPLICATE_OR_SIMILAR, result.status)
        assertTrue(result.coverage.complete)
    }

    @Test
    fun `provider failure can never become no known issue`() {
        val service = service { _, _ -> DurLookupResult.Failure("DUR_TIMEOUT") }

        val result = service.compare(listOf(ingredient("A")), listOf(ingredient("B")))

        assertEquals(InteractionSeverity.UNKNOWN, result.status)
        assertFalse(result.coverage.complete)
        assertEquals(1, result.coverage.failedPairs)
    }

    @Test
    fun `all resolved no-match pairs are no known issue with safety caveat`() {
        val service = service { _, _ -> DurLookupResult.NoMatch }

        val result = service.compare(listOf(ingredient("A")), listOf(ingredient("B")))

        assertEquals(InteractionSeverity.NO_KNOWN_ISSUE, result.status)
        assertTrue(result.coverage.complete)
        assertTrue(result.summary.contains("안전을 보장"))
    }

    @Test
    fun `official prohibited evidence has highest priority`() {
        val officialEvidence = Evidence(
            sourceType = "PUBLIC_DATA",
            sourceName = "공식 DUR",
            sourceRecordId = "DUR-1",
            providerReference = "official",
            retrievedAt = Instant.EPOCH,
            originalMessage = "official message",
            normalizedMessage = "normalized",
            authority = "식품의약품안전처",
            reviewStatus = "OFFICIAL",
        )
        val service = service { _, _ -> DurLookupResult.Prohibited(listOf(officialEvidence)) }

        val result = service.compare(listOf(ingredient("A")), listOf(ingredient("B")))

        assertEquals(InteractionSeverity.PROHIBITED, result.status)
        assertEquals("DUR-1", result.evidence.single().sourceRecordId)
    }

    private fun service(answer: (Ingredient, Ingredient) -> DurLookupResult) =
        IngredientComparisonService(
            durClient = object : DurIngredientApiClient {
                override fun check(left: Ingredient, right: Ingredient): DurLookupResult = answer(left, right)
            },
            coverageCalculator = CoverageCalculator(),
        )

    private fun ingredient(code: String) = Ingredient(
        providerCode = code,
        displayName = code,
        koreanName = code,
        englishName = null,
        normalizedName = code.lowercase(),
        amount = null,
        unit = null,
        source = SourceMetadata("공식 제품정보", "P-$code", Instant.EPOCH, "official"),
    )
}
