package com.haneul.medassist.service

import com.haneul.medassist.config.MatchingProperties
import com.haneul.medassist.domain.medication.IngredientLookupStatus
import com.haneul.medassist.domain.medication.MatchConflict
import com.haneul.medassist.domain.medication.NormalizedDrugName
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import org.springframework.stereotype.Component
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class MatchAssessment(
    val score: Int,
    val reasons: List<String>,
    val conflicts: Set<MatchConflict>,
)

@Component
class DrugProductMatcher(
    private val normalizer: DrugNameNormalizer,
    private val properties: MatchingProperties,
) {
    fun assess(query: NormalizedDrugName, product: VerifiedDrugProduct): MatchAssessment {
        val official = normalizer.normalize(product.productName)
        val reasons = mutableListOf<String>()
        val conflicts = mutableSetOf<MatchConflict>()
        var score = nameScore(query, official, reasons)

        if (query.strengthAmount != null && official.strengthAmount != null) {
            if (query.strengthAmount.compareTo(official.strengthAmount) == 0) {
                score += 15
                reasons += "용량 일치"
            } else {
                score -= 20
                conflicts += MatchConflict.STRENGTH
                reasons += "용량 불일치"
            }
        }

        if (query.strengthUnit != null && official.strengthUnit != null) {
            if (query.strengthUnit == official.strengthUnit) {
                score += 5
                reasons += "단위 일치"
            } else {
                score -= 5
                conflicts += MatchConflict.UNIT
                reasons += "단위 불일치"
            }
        }

        if (query.dosageForm != null && official.dosageForm != null) {
            if (query.dosageForm == official.dosageForm) {
                score += 5
                reasons += "제형 일치"
            } else {
                score -= 5
                conflicts += MatchConflict.DOSAGE_FORM
                reasons += "제형 불일치"
            }
        }

        query.manufacturerHint?.let { hint ->
            val manufacturer = product.manufacturer
            if (manufacturer != null && normalizer.compact(manufacturer).contains(normalizer.compact(hint))) {
                score += 5
                reasons += "제조사 일치"
            } else if (manufacturer != null) {
                score -= 10
                conflicts += MatchConflict.MANUFACTURER
                reasons += "제조사 불일치"
            }
        }

        return MatchAssessment(
            score = score.coerceIn(0, 100),
            reasons = reasons.distinct(),
            conflicts = conflicts,
        )
    }

    fun requiresUserConfirmation(
        assessments: List<Pair<MatchAssessment, IngredientLookupStatus>>,
    ): Boolean {
        if (assessments.isEmpty()) return false
        val sorted = assessments.sortedByDescending { it.first.score }
        val top = sorted.first()
        val gap = if (sorted.size > 1) top.first.score - sorted[1].first.score else Int.MAX_VALUE

        return sorted.size > 1 ||
            top.first.score < properties.autoSelectionScore ||
            gap < properties.minimumScoreGap ||
            top.first.conflicts.isNotEmpty() ||
            top.second != IngredientLookupStatus.RESOLVED
    }

    private fun nameScore(
        query: NormalizedDrugName,
        official: NormalizedDrugName,
        reasons: MutableList<String>,
    ): Int {
        val queryBase = normalizer.compact(query.baseName)
        val officialBase = normalizer.compact(official.baseName)
        if (query.compactQuery == official.compactQuery) {
            reasons += "정규화 제품명 완전 일치"
            return 70
        }
        if (queryBase.isNotBlank() && queryBase == officialBase) {
            reasons += "기본 제품명 일치"
            return 65
        }
        if (queryBase.isNotBlank() && officialBase.startsWith(queryBase)) {
            reasons += "제품명 접두 일치"
            return 55
        }
        if (queryBase.isNotBlank() && officialBase.contains(queryBase)) {
            reasons += "제품명 부분 일치"
            return 45
        }

        val similarity = similarity(queryBase, officialBase)
        reasons += "문자열 유사도 ${"%.2f".format(Locale.ROOT, similarity)}"
        return (similarity * 50).toInt()
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / max(left.length, right.length)
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitution = previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = min(
                    min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                    substitution,
                )
            }
            previous = current
        }
        return previous[right.length]
    }
}
