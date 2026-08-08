package com.haneul.medassist.service

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.domain.interaction.DrugInteractionAnalysisResult
import com.haneul.medassist.domain.interaction.DrugInteractionFailureCode
import com.haneul.medassist.domain.interaction.InteractionCheckStatus
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.exception.MedAssistException
import org.springframework.stereotype.Service
import java.time.Instant

/** Official product -> all official ingredients -> Cartesian DUR comparison. */
@Service
class DrugInteractionAnalysisService(
    private val drugProductApiClient: DrugProductApiClient,
    private val ingredientComparisonService: IngredientComparisonService,
) {
    fun analyze(leftProductCode: String, rightProductCode: String): DrugInteractionAnalysisResult {
        val failedSteps = linkedSetOf<DrugInteractionFailureCode>()
        val left = resolve(
            leftProductCode.trim(),
            DrugInteractionFailureCode.LEFT_PRODUCT_NOT_FOUND,
            DrugInteractionFailureCode.LEFT_PRODUCT_LOOKUP_FAILED,
            DrugInteractionFailureCode.LEFT_INGREDIENT_LOOKUP_FAILED,
            failedSteps,
        )
        val right = resolve(
            rightProductCode.trim(),
            DrugInteractionFailureCode.RIGHT_PRODUCT_NOT_FOUND,
            DrugInteractionFailureCode.RIGHT_PRODUCT_LOOKUP_FAILED,
            DrugInteractionFailureCode.RIGHT_INGREDIENT_LOOKUP_FAILED,
            failedSteps,
        )
        val interaction = if (left != null && right != null) {
            ingredientComparisonService.compare(left.ingredients, right.ingredients).also {
                if (!it.coverage.complete) failedSteps += DrugInteractionFailureCode.PAIR_EVALUATION_INCOMPLETE
            }
        } else {
            null
        }
        val processingStatus = when {
            interaction == null -> InteractionCheckStatus.FAILED
            interaction.coverage.complete -> InteractionCheckStatus.COMPLETED
            else -> InteractionCheckStatus.PARTIAL
        }
        return DrugInteractionAnalysisResult(
            processingStatus = processingStatus,
            leftProduct = left?.product,
            rightProduct = right?.product,
            leftIngredients = left?.ingredients.orEmpty(),
            rightIngredients = right?.ingredients.orEmpty(),
            interaction = interaction,
            failedSteps = failedSteps,
            analyzedAt = Instant.now(),
        )
    }

    private fun resolve(
        productCode: String,
        notFoundCode: DrugInteractionFailureCode,
        productFailureCode: DrugInteractionFailureCode,
        ingredientFailureCode: DrugInteractionFailureCode,
        failedSteps: MutableSet<DrugInteractionFailureCode>,
    ): ResolvedDrug? {
        val product = try {
            drugProductApiClient.findProduct(productCode)
        } catch (_: MedAssistException) {
            failedSteps += productFailureCode
            return null
        }
        if (product == null) {
            failedSteps += notFoundCode
            return null
        }
        val ingredientResult = try {
            drugProductApiClient.findIngredients(product.productCode, product.productName)
        } catch (_: MedAssistException) {
            failedSteps += ingredientFailureCode
            return null
        }
        return when (ingredientResult) {
            is IngredientSearchResult.Success -> if (ingredientResult.ingredients.isEmpty()) {
                failedSteps += ingredientFailureCode
                null
            } else {
                ResolvedDrug(product, ingredientResult.ingredients)
            }
            is IngredientSearchResult.ProviderError,
            IngredientSearchResult.SchemaUnverified,
            -> {
                failedSteps += ingredientFailureCode
                null
            }
        }
    }

    private data class ResolvedDrug(
        val product: VerifiedDrugProduct,
        val ingredients: List<Ingredient>,
    )
}
