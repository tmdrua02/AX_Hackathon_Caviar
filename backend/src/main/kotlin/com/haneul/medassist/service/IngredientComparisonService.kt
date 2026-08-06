package com.haneul.medassist.service

import com.haneul.medassist.client.dur.DurIngredientApiClient
import com.haneul.medassist.client.dur.DurPairLookupResult
import com.haneul.medassist.domain.interaction.Evidence
import com.haneul.medassist.domain.interaction.IngredientPairResult
import com.haneul.medassist.domain.interaction.InteractionResult
import com.haneul.medassist.domain.interaction.InteractionSeverity
import com.haneul.medassist.domain.interaction.PairStatus
import com.haneul.medassist.domain.medication.Ingredient
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class IngredientComparisonService(
    private val durClient: DurIngredientApiClient,
    private val coverageCalculator: CoverageCalculator,
) {
    fun compare(left: List<Ingredient>, right: List<Ingredient>): InteractionResult {
        val pairs = left.flatMap { leftIngredient ->
            right.map { rightIngredient -> comparePair(leftIngredient, rightIngredient) }
        }
        val coverage = coverageCalculator.calculate(left, right, pairs)
        val severity = finalSeverity(pairs, coverage.complete)
        return InteractionResult(
            status = severity,
            summary = summary(severity),
            ingredientPairs = pairs,
            evidence = pairs.flatMap { it.evidence }.distinctBy { it.sourceName to it.sourceRecordId },
            coverage = coverage,
        )
    }

    private fun comparePair(left: Ingredient, right: Ingredient): IngredientPairResult {
        val leftCode = left.providerCode
        val rightCode = right.providerCode
        if (leftCode.isNullOrBlank() || rightCode.isNullOrBlank()) {
            return IngredientPairResult(left, right, PairStatus.FAILED, emptyList(), "INGREDIENT_CODE_UNRESOLVED")
        }
        if (leftCode == rightCode) {
            return IngredientPairResult(
                left = left,
                right = right,
                status = PairStatus.DUPLICATE,
                evidence = listOf(duplicateEvidence(left, right)),
            )
        }
        return when (val dur = durClient.check(left, right)) {
            is DurPairLookupResult.Prohibited -> IngredientPairResult(left, right, PairStatus.PROHIBITED, dur.evidence)
            is DurPairLookupResult.Caution -> IngredientPairResult(left, right, PairStatus.CAUTION, dur.evidence)
            DurPairLookupResult.NoMatch -> IngredientPairResult(left, right, PairStatus.NO_MATCH, emptyList())
            is DurPairLookupResult.Failure -> IngredientPairResult(left, right, PairStatus.FAILED, emptyList(), dur.safeErrorCode)
        }
    }

    private fun finalSeverity(pairs: List<IngredientPairResult>, complete: Boolean): InteractionSeverity = when {
        pairs.any { it.status == PairStatus.PROHIBITED } -> InteractionSeverity.PROHIBITED
        pairs.any { it.status == PairStatus.CAUTION } -> InteractionSeverity.CAUTION
        pairs.any { it.status == PairStatus.DUPLICATE } -> InteractionSeverity.DUPLICATE_OR_SIMILAR
        complete && pairs.all { it.status == PairStatus.NO_MATCH } -> InteractionSeverity.NO_KNOWN_ISSUE
        else -> InteractionSeverity.UNKNOWN
    }

    private fun summary(severity: InteractionSeverity): String = when (severity) {
        InteractionSeverity.PROHIBITED -> "공식 DUR 병용금기 근거가 확인되었습니다."
        InteractionSeverity.CAUTION -> "공식 주의 근거가 확인되었습니다."
        InteractionSeverity.DUPLICATE_OR_SIMILAR -> "동일한 공식 성분코드가 확인되었습니다."
        InteractionSeverity.NO_KNOWN_ISSUE -> "조회한 공식 정보에서 알려진 상호작용을 찾지 못했습니다. 안전을 보장하는 의미는 아닙니다."
        InteractionSeverity.UNKNOWN -> "성분 또는 공식 정보를 완전히 확인하지 못했습니다. 안전하다는 의미가 아닙니다."
    }

    private fun duplicateEvidence(left: Ingredient, right: Ingredient): Evidence = Evidence(
        sourceType = "PUBLIC_DATA",
        sourceName = left.source.name,
        sourceRecordId = listOf(left.source.recordId, right.source.recordId).distinct().joinToString(","),
        providerReference = left.source.providerReference,
        retrievedAt = Instant.now(),
        originalMessage = "두 제품에서 동일한 공식 성분코드가 확인됨",
        normalizedMessage = "same official ingredient code",
        authority = "식품의약품안전처",
        reviewStatus = "OFFICIAL_CODE_MATCH",
    )
}
