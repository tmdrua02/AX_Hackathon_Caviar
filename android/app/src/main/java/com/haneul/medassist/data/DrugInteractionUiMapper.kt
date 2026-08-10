package com.haneul.medassist.data

import java.time.Instant
import java.util.UUID

internal object DrugInteractionUiMapper {
    data class BatchOutcome(
        val reference: Medication,
        val comparisons: List<Medication>,
        val response: DrugInteractionBatchResponse? = null,
        val failureMessage: String? = null,
    )

    fun mapSelected(
        selected: List<Medication>,
        outcomes: List<BatchOutcome>,
    ): InteractionCheck {
        val snapshot = selected.filter { it.active }.distinctBy { it.id }
        val mappedByPair = linkedMapOf<String, InteractionResult>()
        outcomes.forEach { outcome ->
            if (outcome.response != null) {
                map(outcome.response, outcome.reference, outcome.comparisons).results.forEach { result ->
                    mappedByPair[pairKey(result.newMedication, result.existingMedication)] = result
                }
            } else {
                outcome.comparisons.forEach { comparison ->
                    mappedByPair[pairKey(outcome.reference, comparison)] = unsupportedResult(
                        outcome.reference,
                        comparison,
                        outcome.failureMessage ?: "공식 성분·DUR 서버에서 이 제품 조합을 분석하지 못했습니다.",
                    )
                }
            }
        }

        val results = InteractionAnalysisPlanner.allPairs(snapshot).map { pair ->
            when {
                pair.left.productType == ProductType.HEALTH_SUPPLEMENT ||
                    pair.right.productType == ProductType.HEALTH_SUPPLEMENT -> unsupportedResult(
                    pair.left,
                    pair.right,
                    "현재 복용 목록에는 건강기능식품의 공식 신고번호가 저장되지 않아 약–건강기능식품 분석을 자동 실행할 수 없습니다. 건강기능식품 검색에서 공식 제품을 선택해 확인해 주세요.",
                )
                !InteractionAnalysisPlanner.hasOfficialDrugIdentity(pair.left) ||
                    !InteractionAnalysisPlanner.hasOfficialDrugIdentity(pair.right) -> unsupportedResult(
                    pair.left,
                    pair.right,
                    "공식 품목기준코드 또는 공식 성분코드가 없는 제품이 포함되어 성분·DUR 분석을 실행할 수 없습니다.",
                )
                else -> mappedByPair[pairKey(pair.left, pair.right)] ?: unsupportedResult(
                    pair.left,
                    pair.right,
                    "공식 분석 응답에서 이 제품 조합을 찾지 못했습니다. 안전하다는 의미가 아닙니다.",
                )
            }
        }.distinctBy { pairKey(it.newMedication, it.existingMedication) }

        val unknownCount = results.count { it.severity == Severity.UNKNOWN }
        val remotePartial = outcomes.any { it.response?.processingStatus == "PARTIAL" }
        val remoteFailed = outcomes.any { it.response?.processingStatus == "FAILED" || it.failureMessage != null }
        val status = when {
            results.isEmpty() -> "EMPTY"
            unknownCount == results.size && remoteFailed -> "FAILED"
            unknownCount == results.size -> "PARTIAL"
            unknownCount > 0 || remotePartial || remoteFailed -> "PARTIAL"
            else -> "COMPLETED"
        }
        val coverage = outcomes.mapNotNull { it.response?.coverage }
        return InteractionCheck(
            id = UUID.randomUUID().toString(),
            jobId = UUID.randomUUID().toString(),
            status = status,
            results = results,
            coverage = Coverage(
                identifiedIngredients = snapshot.flatMap { it.ingredients }
                    .map { it.normalizedName }.filter(String::isNotBlank).distinct().size,
                successfulQueries = coverage.sumOf { it.completedIngredientPairs },
                unidentifiedIngredients = coverage.sumOf { it.failedIngredientPairs } + unknownCount,
                providerError = remoteFailed || coverage.any { it.failedComparisons > 0 },
            ),
            saved = false,
            disclaimer = outcomes.mapNotNull { it.response?.disclaimer }.firstOrNull()
                ?: "정보 제공용이며 복용을 시작·중단·변경하기 전에 의사 또는 약사와 상담하세요.",
            analyzedMedications = snapshot,
            analyzedAt = outcomes.mapNotNull { it.response?.analyzedAt }.maxOrNull()
                ?: Instant.now().toString(),
        )
    }

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

    private fun pairKey(left: Medication, right: Medication): String =
        listOf(left.id, right.id).sorted().joinToString("|")

    private fun title(severity: Severity): String = when (severity) {
        Severity.PROHIBITED -> "공식 DUR 병용금기 정보 확인"
        Severity.CAUTION -> "복용 전 전문가 확인 필요"
        Severity.DUPLICATE_OR_SIMILAR -> "동일 성분 또는 유사 효능 가능성"
        Severity.NO_KNOWN_ISSUE -> "확인된 공식 데이터 범위 내 특이사항 없음"
        Severity.UNKNOWN -> "확인 불가 · 전문가 확인 필요"
    }
}
