package com.haneul.medassist.domain.medication

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SourceMetadata(
    val name: String,
    val recordId: String,
    val retrievedAt: Instant,
    val providerReference: String,
)

data class Ingredient(
    val providerCode: String?,
    val displayName: String,
    val koreanName: String?,
    val englishName: String?,
    val normalizedName: String,
    val amount: BigDecimal?,
    val unit: String?,
    val saltForm: String? = null,
    val hydrateForm: String? = null,
    val source: SourceMetadata,
)

enum class IngredientLookupStatus {
    RESOLVED,
    NOT_FOUND,
    SCHEMA_UNVERIFIED,
    PROVIDER_ERROR,
}

enum class ProductResolutionStatus {
    RESOLVED,
    NEEDS_CONFIRMATION,
    NOT_FOUND,
    PROVIDER_ERROR,
}

data class VerifiedDrugProduct(
    val productCode: String,
    val productName: String,
    val manufacturer: String?,
    val source: SourceMetadata,
)

data class DrugProductCandidate(
    val product: VerifiedDrugProduct,
    val ingredients: List<Ingredient>,
    val matchConfidence: Int,
    val matchReasons: List<String>,
    val ingredientLookupStatus: IngredientLookupStatus,
    val conflicts: Set<MatchConflict>,
)

enum class MatchConflict {
    STRENGTH,
    UNIT,
    DOSAGE_FORM,
    MANUFACTURER,
}

data class Medication(
    val id: UUID,
    val productCode: String?,
    val productName: String,
    val manufacturer: String?,
    val ingredients: List<Ingredient>,
    val source: SourceMetadata?,
    val resolutionStatus: ProductResolutionStatus,
)
