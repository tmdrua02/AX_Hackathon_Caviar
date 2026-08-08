package com.haneul.medassist.client.dur

import com.haneul.medassist.domain.interaction.InteractionSeverity
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.service.CoverageCalculator
import com.haneul.medassist.service.IngredientComparisonService
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DurPairLookupTest {
    @Test
    fun `pair lookup uses both official ingredient code directions and maps active prohibition`() {
        val requests = mutableListOf<DurLookupRequest>()
        val client = client { request ->
            requests += request
            if (request.lookupDirection == DurLookupDirection.FORWARD) {
                result(records = listOf(record("D-A", "D-B")))
            } else {
                result(records = emptyList(), status = DurLookupStatus.NO_MATCH)
            }
        }

        val pair = client.check(ingredient("D-A"), ingredient("D-B"))

        val prohibited = assertIs<DurPairLookupResult.Prohibited>(pair)
        assertTrue(prohibited.complete)
        assertEquals(listOf(DurLookupDirection.FORWARD, DurLookupDirection.REVERSE), requests.map { it.lookupDirection })
        assertEquals(listOf("D-A", "D-B"), requests.map { it.ingredientCode })
        assertNull(prohibited.evidence.single().sourceRecordId)
        assertEquals("공식 병용금기 원문", prohibited.evidence.single().originalMessage)
    }

    @Test
    fun `two complete directions without target record are no match`() {
        val client = client { request ->
            result(
                records = if (request.lookupDirection == DurLookupDirection.FORWARD) {
                    listOf(record("D-A", "D-C"))
                } else {
                    emptyList()
                },
                status = DurLookupStatus.MATCHED,
            )
        }

        assertEquals(DurPairLookupResult.NoMatch, client.check(ingredient("D-A"), ingredient("D-B")))
    }

    @Test
    fun `active prohibition survives reverse lookup failure with incomplete coverage`() {
        val client = client { request ->
            if (request.lookupDirection == DurLookupDirection.FORWARD) {
                result(records = listOf(record("D-A", "D-B")))
            } else {
                result(
                    records = emptyList(),
                    status = DurLookupStatus.FAILED,
                    complete = false,
                    errorCode = "PUBLIC_API_TIMEOUT",
                )
            }
        }
        val service = IngredientComparisonService(client, CoverageCalculator())

        val analysis = service.compare(listOf(ingredient("D-A")), listOf(ingredient("D-B")))

        assertEquals(InteractionSeverity.PROHIBITED, analysis.status)
        assertFalse(analysis.coverage.complete)
        assertEquals(1, analysis.coverage.failedPairs)
        assertEquals("PUBLIC_API_TIMEOUT", analysis.ingredientPairs.single().safeErrorCode)
    }

    @Test
    fun `unknown target provider status is a safe failure`() {
        val client = client { request ->
            if (request.lookupDirection == DurLookupDirection.FORWARD) {
                result(records = listOf(record("D-A", "D-B", DurProviderStatus.UNKNOWN)))
            } else {
                result(records = emptyList(), status = DurLookupStatus.NO_MATCH)
            }
        }

        val pair = assertIs<DurPairLookupResult.Failure>(client.check(ingredient("D-A"), ingredient("D-B")))

        assertEquals("DUR_PROVIDER_STATUS_UNKNOWN", pair.safeErrorCode)
    }

    private fun client(answer: (DurLookupRequest) -> DurLookupResult) = object : DurIngredientApiClient {
        override fun lookup(request: DurLookupRequest): DurLookupResult = answer(request)
    }

    private fun result(
        records: List<DurProviderRecord>,
        status: DurLookupStatus = if (records.isEmpty()) DurLookupStatus.NO_MATCH else DurLookupStatus.MATCHED,
        complete: Boolean = true,
        errorCode: String? = null,
    ) = DurLookupResult(
        status = status,
        records = records,
        totalCount = records.size,
        completedPages = if (complete) listOf(1) else emptyList(),
        failedPages = if (complete) emptyList() else listOf(1),
        complete = complete,
        retrievedAt = Instant.EPOCH,
        providerResultCode = if (complete) "00" else null,
        errorCode = errorCode,
    )

    private fun record(
        ingredientCode: String,
        relatedCode: String,
        status: DurProviderStatus = DurProviderStatus.ACTIVE,
    ) = DurProviderRecord(
        providerRecordId = null,
        typeName = "병용금기",
        ingredientCode = ingredientCode,
        ingredientEnglishName = null,
        ingredientKoreanName = ingredientCode,
        relatedIngredientCode = relatedCode,
        relatedIngredientEnglishName = null,
        relatedIngredientKoreanName = relatedCode,
        prohibitionContent = "공식 병용금기 원문",
        notificationDate = "20260807",
        remark = null,
        providerStatus = status,
        rawFields = emptyMap(),
    )

    private fun ingredient(code: String) = Ingredient(
        providerCode = code,
        displayName = code,
        koreanName = code,
        englishName = null,
        normalizedName = code.lowercase(),
        amount = null,
        unit = null,
        source = SourceMetadata("식약처 의약품", "P-$code", Instant.EPOCH, "MFDS_DRUG_PRODUCT"),
    )
}
