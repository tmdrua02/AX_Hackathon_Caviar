package com.haneul.medassist.dto.drug

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class DrugProductSearchRequest(
    @field:NotBlank(message = "약품명을 입력해 주세요.")
    @field:Size(min = 2, max = 100, message = "약품명은 2자 이상 100자 이하로 입력해 주세요.")
    val query: String,
)

data class DrugProductSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val candidates: List<DrugProductCandidateResponse>,
    val requiresUserConfirmation: Boolean,
    val coverage: ProductSearchCoverage,
    val disclaimer: String = CONSULTATION_NOTICE,
) {
    companion object {
        const val CONSULTATION_NOTICE =
            "정보 제공용이며 복용을 시작·중단·변경하기 전에 의사 또는 약사와 상담하세요."
    }
}

data class DrugProductCandidateResponse(
    val productCode: String,
    val productName: String,
    val manufacturer: String?,
    val ingredients: List<IngredientResponse>,
    val matchConfidence: Int,
    val matchReasons: List<String>,
    val ingredientLookupStatus: String,
    val source: SourceResponse,
)

data class IngredientResponse(
    val displayName: String,
    val normalizedName: String,
    val providerCode: String?,
    val amount: BigDecimal?,
    val unit: String?,
)

data class SourceResponse(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)

data class ProductSearchCoverage(
    val productCandidates: Int,
    val ingredientLookupsCompleted: Int,
    val ingredientLookupsFailed: Int,
    val complete: Boolean,
)
