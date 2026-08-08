package com.haneul.medassist.service

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.domain.medication.DrugOverview
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.Medication
import com.haneul.medassist.domain.medication.ProductResolutionStatus
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.SupplementInteractionFailureCode
import com.haneul.medassist.domain.supplement.SupplementProductSnapshot
import com.haneul.medassist.exception.MedAssistException
import org.springframework.stereotype.Component
import java.util.UUID

sealed interface MedicationEvidenceResolution {
    data class Resolved(
        val product: VerifiedDrugProduct,
        val ingredients: List<Ingredient>,
        val ingredientsComplete: Boolean,
        val overview: DrugOverview?,
        val optionalFailedSteps: Set<SupplementInteractionFailureCode> = emptySet(),
    ) : MedicationEvidenceResolution

    data object NotFound : MedicationEvidenceResolution

    data class Failed(
        val failureCode: SupplementInteractionFailureCode,
        val providerErrorCode: String? = null,
    ) : MedicationEvidenceResolution
}

fun interface MedicationEvidenceProvider {
    fun resolve(productCode: String): MedicationEvidenceResolution
}

sealed interface SupplementProductEvidenceResolution {
    data class Resolved(val product: SupplementProductSnapshot) : SupplementProductEvidenceResolution

    data object NotFound : SupplementProductEvidenceResolution

    data class Failed(
        val failureCode: SupplementInteractionFailureCode,
        val providerErrorCode: String? = null,
    ) : SupplementProductEvidenceResolution
}

fun interface SupplementProductEvidenceProvider {
    fun resolve(statementNo: String): SupplementProductEvidenceResolution
}

@Component
class PublicDataMedicationEvidenceProvider(
    private val drugProductApiClient: DrugProductApiClient,
    private val drugOverviewService: DrugOverviewService,
) : MedicationEvidenceProvider {
    override fun resolve(productCode: String): MedicationEvidenceResolution = try {
        val product = drugProductApiClient.findProduct(productCode) ?: return MedicationEvidenceResolution.NotFound
        when (val ingredientResult = drugProductApiClient.findIngredients(product.productCode, product.productName)) {
            is IngredientSearchResult.ProviderError -> MedicationEvidenceResolution.Failed(
                SupplementInteractionFailureCode.MEDICATION_INGREDIENT_LOOKUP_FAILED,
                ingredientResult.safeErrorCode,
            )
            IngredientSearchResult.SchemaUnverified -> MedicationEvidenceResolution.Failed(
                SupplementInteractionFailureCode.MEDICATION_INGREDIENT_LOOKUP_FAILED,
                "INGREDIENT_SCHEMA_UNVERIFIED",
            )
            is IngredientSearchResult.Success -> {
                val overviewResult = runCatching {
                    drugOverviewService.findOverview(
                        Medication(
                            id = UUID.randomUUID(),
                            productCode = product.productCode,
                            productName = product.productName,
                            manufacturer = product.manufacturer,
                            ingredients = ingredientResult.ingredients,
                            source = product.source,
                            resolutionStatus = ProductResolutionStatus.RESOLVED,
                        ),
                    )
                }.getOrNull()
                val overview = overviewResult?.overview
                val optionalFailures = if (
                    overviewResult == null || overviewResult.status == DrugOverviewLookupStatus.FAILED ||
                    overviewResult.status == DrugOverviewLookupStatus.PARTIAL
                ) {
                    setOf(SupplementInteractionFailureCode.MEDICATION_OVERVIEW_LOOKUP_FAILED)
                } else {
                    emptySet()
                }
                MedicationEvidenceResolution.Resolved(
                    product = product,
                    ingredients = ingredientResult.ingredients,
                    ingredientsComplete = ingredientResult.ingredients.isNotEmpty(),
                    overview = overview,
                    optionalFailedSteps = optionalFailures,
                )
            }
        }
    } catch (exception: MedAssistException) {
        MedicationEvidenceResolution.Failed(
            SupplementInteractionFailureCode.MEDICATION_PRODUCT_LOOKUP_FAILED,
            exception.errorCode.name,
        )
    }
}

@Component
class PublicDataSupplementProductEvidenceProvider(
    private val service: HealthFunctionalFoodService,
) : SupplementProductEvidenceProvider {
    override fun resolve(statementNo: String): SupplementProductEvidenceResolution {
        val result = try {
            service.findByStatementNo(statementNo)
        } catch (exception: MedAssistException) {
            return SupplementProductEvidenceResolution.Failed(
                SupplementInteractionFailureCode.SUPPLEMENT_PRODUCT_LOOKUP_FAILED,
                exception.errorCode.name,
            )
        }
        return when (result.status) {
            HealthFunctionalFoodLookupStatus.RESOLVED -> result.snapshot
                ?.let(SupplementProductEvidenceResolution::Resolved)
                ?: SupplementProductEvidenceResolution.Failed(
                    SupplementInteractionFailureCode.SUPPLEMENT_PRODUCT_LOOKUP_FAILED,
                    "SUPPLEMENT_INVALID_RESPONSE",
                )

            HealthFunctionalFoodLookupStatus.NOT_FOUND -> SupplementProductEvidenceResolution.NotFound
            HealthFunctionalFoodLookupStatus.FAILED,
            HealthFunctionalFoodLookupStatus.PARTIAL,
            -> SupplementProductEvidenceResolution.Failed(
                SupplementInteractionFailureCode.SUPPLEMENT_PRODUCT_LOOKUP_FAILED,
                result.errorCode ?: "SUPPLEMENT_PROVIDER_FAILED",
            )
        }
    }
}
