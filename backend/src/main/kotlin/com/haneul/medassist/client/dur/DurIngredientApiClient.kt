package com.haneul.medassist.client.dur

import com.haneul.medassist.domain.interaction.Evidence
import com.haneul.medassist.domain.medication.Ingredient
import java.time.Instant

interface DurIngredientApiClient {
    fun lookup(request: DurLookupRequest): DurLookupResult = DurLookupResult(
        status = DurLookupStatus.FAILED,
        records = emptyList(),
        totalCount = null,
        completedPages = emptyList(),
        failedPages = emptyList(),
        complete = false,
        retrievedAt = Instant.now(),
        errorCode = DUR_SCHEMA_UNVERIFIED,
    )

    fun findContraindications(
        ingredientCode: String,
        ingredientKoreanName: String?,
    ): DurLookupResult = lookup(
        DurLookupRequest(
            ingredientCode = ingredientCode,
            ingredientKoreanName = ingredientKoreanName,
            lookupDirection = DurLookupDirection.FORWARD,
        ),
    )

    /** 공식 성분코드 쌍을 양방향으로 조회한다. 건강기능식품 원료에는 사용하지 않는다. */
    fun check(left: Ingredient, right: Ingredient): DurPairLookupResult {
        val leftCode = left.providerCode?.takeIf(String::isNotBlank)
            ?: return DurPairLookupResult.Failure("DUR_LEFT_INGREDIENT_CODE_MISSING")
        val rightCode = right.providerCode?.takeIf(String::isNotBlank)
            ?: return DurPairLookupResult.Failure("DUR_RIGHT_INGREDIENT_CODE_MISSING")
        val leftName = left.koreanName?.takeIf(String::isNotBlank)
            ?: return DurPairLookupResult.Failure("DUR_LEFT_INGREDIENT_NAME_MISSING")
        val rightName = right.koreanName?.takeIf(String::isNotBlank)
            ?: return DurPairLookupResult.Failure("DUR_RIGHT_INGREDIENT_NAME_MISSING")

        val forward = lookup(DurLookupRequest(leftCode, leftName, DurLookupDirection.FORWARD))
        val reverse = lookup(DurLookupRequest(rightCode, rightName, DurLookupDirection.REVERSE))
        val matching = buildList {
            addAll(forward.records.filter { it.relatedIngredientCode == rightCode })
            addAll(reverse.records.filter { it.relatedIngredientCode == leftCode })
        }
        val active = matching.filter { it.providerStatus == DurProviderStatus.ACTIVE }
            .distinctBy { listOf(it.ingredientCode, it.relatedIngredientCode, it.notificationDate, it.prohibitionContent) }
        val complete = forward.complete && reverse.complete
        val failureCode = listOfNotNull(
            forward.errorCode.takeUnless { forward.complete },
            reverse.errorCode.takeUnless { reverse.complete },
        ).firstOrNull() ?: "DUR_PAIR_LOOKUP_INCOMPLETE"
        if (active.isNotEmpty()) {
            return DurPairLookupResult.Prohibited(
                evidence = active.map { record ->
                    Evidence(
                        sourceType = "PUBLIC_DATA",
                        sourceName = "식약처 DUR 병용금기",
                        sourceRecordId = record.providerRecordId,
                        providerReference = "MFDS_DUR:getUsjntTabooInfoList02",
                        retrievedAt = maxOf(forward.retrievedAt, reverse.retrievedAt),
                        originalMessage = record.prohibitionContent,
                        normalizedMessage = null,
                        authority = "식품의약품안전처",
                        reviewStatus = "OFFICIAL_PROVIDER_ACTIVE",
                    )
                },
                complete = complete,
                safeErrorCode = failureCode.takeUnless { complete },
            )
        }
        if (matching.any { it.providerStatus == DurProviderStatus.UNKNOWN }) {
            return DurPairLookupResult.Failure("DUR_PROVIDER_STATUS_UNKNOWN")
        }
        return if (complete) DurPairLookupResult.NoMatch else DurPairLookupResult.Failure(failureCode)
    }

    companion object {
        const val DUR_SCHEMA_UNVERIFIED = "DUR_SCHEMA_UNVERIFIED"
    }
}

sealed interface DurPairLookupResult {
    data class Prohibited(
        val evidence: List<Evidence>,
        val complete: Boolean = true,
        val safeErrorCode: String? = null,
    ) : DurPairLookupResult

    data class Caution(
        val evidence: List<Evidence>,
        val complete: Boolean = true,
        val safeErrorCode: String? = null,
    ) : DurPairLookupResult
    data object NoMatch : DurPairLookupResult
    data class Failure(val safeErrorCode: String) : DurPairLookupResult
}
