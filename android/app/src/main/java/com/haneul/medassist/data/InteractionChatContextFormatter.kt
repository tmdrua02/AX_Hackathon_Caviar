package com.haneul.medassist.data

internal object InteractionChatContextFormatter {
    private const val MAX_CONTEXT_LENGTH = 10_000

    fun format(check: InteractionCheck?): String? {
        if (check == null) return null
        return buildString {
            appendLine("공식 동시복용 분석 상태: ${check.status}")
            appendLine(
                "조회 범위: 확인 성분 ${check.coverage.identifiedIngredients}개, " +
                    "완료 쌍 ${check.coverage.successfulQueries}개, 미확인 ${check.coverage.unidentifiedIngredients}개",
            )
            check.results.forEach { result ->
                appendLine("조합: ${result.newMedication.name} / ${result.existingMedication.name}")
                appendLine("판정: ${result.severity}")
                appendLine("설명: ${result.easyExplanation}")
                if (result.evidence.isEmpty()) {
                    appendLine("공식 근거: 없음(안전하다는 의미가 아님)")
                } else {
                    result.evidence.forEach { evidence ->
                        appendLine(
                            "공식 근거: ${evidence.ingredientA.orEmpty()} / ${evidence.ingredientB.orEmpty()}, " +
                                "${evidence.evidenceType.orEmpty()}, ${evidence.sourceName}, " +
                                "기록 ${evidence.sourceRecordId.orEmpty()}, 조회 ${evidence.retrievedAt}",
                        )
                    }
                }
            }
            append("주의: ${check.disclaimer}")
        }.take(MAX_CONTEXT_LENGTH)
    }
}
