package com.haneul.medassist.data

import java.time.Instant
import java.util.UUID

internal object DrugInteractionUiMapper {
    fun map(
        response: DrugInteractionBatchResponse?,
        added: Medication,
        existing: List<Medication>,
    ): InteractionCheck {
        val remoteByCode = response?.results.orEmpty().associateBy { it.requestedExistingProductCode }
        val results = existing.map { current ->
            val productCode = current.productCode?.trim().orEmpty()
            val remote = remoteByCode[productCode]
            when {
                current.productType == ProductType.HEALTH_SUPPLEMENT -> unsupportedResult(
                    added,
                    current,
                    "건강기능식품은 아래의 약–건강기능식품 병용 확인에서 공식 제품을 선택해 분석해 주세요.",
                )
                productCode.isBlank() -> unsupportedResult(
                    added,
                    current,
                    "기존 약의 공식 품목기준코드가 없어 성분·DUR 조회를 시작할 수 없습니다.",
                )
                remote == null -> unsupportedResult(
                    added,
                    current,
                    "공식 분석 응답에서 해당 제품 결과를 찾지 못했습니다. 안전하다는 의미가 아닙니다.",
                )
                else -> remote.toUiResult(added, current)
            }
        }
        val remoteCoverage = response?.coverage
        val missingProducts = existing.count {
            it.productType == ProductType.HEALTH_SUPPLEMENT || it.productCode.isNullOrBlank()
        }
        val identifiedIngredients = results.flatMap {
            it.newMedication.ingredients + it.existingMedication.ingredients
        }.map { it.normalizedName }.filter(String::isNotBlank).distinct().size
        return InteractionCheck(
            id = UUID.randomUUID().toString(),
            jobId = UUID.randomUUID().toString(),
            status = response?.processingStatus ?: "PARTIAL",
            results = results,
            coverage = Coverage(
                identifiedIngredients = identifiedIngredients,
                successfulQueries = remoteCoverage?.completedIngredientPairs ?: 0,
                unidentifiedIngredients = (remoteCoverage?.failedIngredientPairs ?: 0) + missingProducts,
                providerError = (remoteCoverage?.failedComparisons ?: 0) > 0,
            ),
            saved = false,
            disclaimer = response?.disclaimer
                ?: "정보 제공용이며 복용을 시작·중단·변경하기 전에 의사 또는 약사와 상담하세요.",
        )
    }

    private fun DrugInteractionPairResponse.toUiResult(
        added: Medication,
        existing: Medication,
    ): InteractionResult {
        val resolvedSeverity = severity ?: Severity.UNKNOWN
        return InteractionResult(
            id = UUID.randomUUID().toString(),
            newMedication = added,
            existingMedication = existing,
            severity = resolvedSeverity,
            title = title(resolvedSeverity),
            easyExplanation = if (failedSteps.isEmpty()) summary else "$summary (${failedSteps.joinToString()})",
            evidence = evidence.map {
                Evidence(
                    ingredientA = it.ingredientA,
                    ingredientB = it.ingredientB,
                    evidenceType = it.evidenceType,
                    sourceName = it.sourceName,
                    sourceUrl = it.sourceUrl,
                    sourceRecordId = it.sourceRecordId,
                    retrievedAt = it.retrievedAt,
                    originalSummary = it.originalSummary,
                    sourceType = it.sourceType,
                )
            },
        )
    }

    private fun unsupportedResult(added: Medication, existing: Medication, reason: String) = InteractionResult(
        id = UUID.randomUUID().toString(),
        newMedication = added,
        existingMedication = existing,
        severity = Severity.UNKNOWN,
        title = title(Severity.UNKNOWN),
        easyExplanation = reason,
        evidence = emptyList(),
    )

    private fun title(severity: Severity): String = when (severity) {
        Severity.PROHIBITED -> "공식 DUR 병용금기 정보 확인"
        Severity.CAUTION -> "복용 전 전문가 확인 필요"
        Severity.DUPLICATE_OR_SIMILAR -> "동일 성분 또는 유사 효능 가능성"
        Severity.NO_KNOWN_ISSUE -> "확인된 공식 데이터 범위 내 특이사항 없음"
        Severity.UNKNOWN -> "확인 불가 · 전문가 확인 필요"
    }
}
