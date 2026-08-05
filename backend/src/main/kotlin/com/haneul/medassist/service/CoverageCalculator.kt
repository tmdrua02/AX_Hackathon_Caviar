package com.haneul.medassist.service

import com.haneul.medassist.domain.interaction.Coverage
import com.haneul.medassist.domain.interaction.IngredientPairResult
import com.haneul.medassist.domain.interaction.PairStatus
import com.haneul.medassist.domain.medication.Ingredient
import org.springframework.stereotype.Component

@Component
class CoverageCalculator {
    fun calculate(
        left: List<Ingredient>,
        right: List<Ingredient>,
        pairs: List<IngredientPairResult>,
    ): Coverage {
        val totalIngredients = left.size + right.size
        val resolvedIngredients = (left + right).count { !it.providerCode.isNullOrBlank() }
        val completedPairs = pairs.count { it.status != PairStatus.FAILED }
        val failedPairs = pairs.size - completedPairs
        val percentage = if (pairs.isEmpty()) 0 else completedPairs * 100 / pairs.size
        return Coverage(
            totalProducts = 2,
            resolvedProducts = if (left.isNotEmpty() && right.isNotEmpty()) 2 else listOf(left, right).count { it.isNotEmpty() },
            totalIngredients = totalIngredients,
            resolvedIngredients = resolvedIngredients,
            totalPairs = pairs.size,
            completedPairs = completedPairs,
            failedPairs = failedPairs,
            percentage = percentage,
            complete = pairs.isNotEmpty() && failedPairs == 0 && resolvedIngredients == totalIngredients,
        )
    }
}
