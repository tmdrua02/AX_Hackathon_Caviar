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

    /** InteractionCheck 연결은 별도 검증과 승인 전까지 안전 실패 상태를 유지한다. */
    fun check(left: Ingredient, right: Ingredient): DurPairLookupResult =
        DurPairLookupResult.Failure(DUR_SCHEMA_UNVERIFIED)

    companion object {
        const val DUR_SCHEMA_UNVERIFIED = "DUR_SCHEMA_UNVERIFIED"
    }
}

sealed interface DurPairLookupResult {
    data class Prohibited(val evidence: List<Evidence>) : DurPairLookupResult
    data class Caution(val evidence: List<Evidence>) : DurPairLookupResult
    data object NoMatch : DurPairLookupResult
    data class Failure(val safeErrorCode: String) : DurPairLookupResult
}
