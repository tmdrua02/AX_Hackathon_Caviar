package com.haneul.medassist.client.dur

data class DurProviderResponse(
    val header: DurProviderHeader,
    val body: DurProviderBody,
)

data class DurProviderHeader(
    val resultCode: String,
    val resultMsg: String,
)

data class DurProviderBody(
    val pageNo: Int,
    val totalCount: Int,
    val numOfRows: Int,
    val items: List<DurProviderItemWrapper>,
)

data class DurProviderItemWrapper(
    val item: DurProviderItem,
)

data class DurProviderItem(
    val typeName: String?,
    val mixType: String?,
    val ingredientCode: String?,
    val ingredientEnglishName: String?,
    val ingredientKoreanName: String?,
    val mix: String?,
    val originalIngredientText: String?,
    val classification: String?,
    val mixtureMixType: String?,
    val relatedIngredientCode: String?,
    val relatedIngredientEnglishName: String?,
    val relatedIngredientKoreanName: String?,
    val mixtureMix: String?,
    val mixtureOriginalText: String?,
    val mixtureClassification: String?,
    val notificationDate: String?,
    val prohibitionContent: String?,
    val remark: String?,
    val deletionStatus: String?,
    val rawFields: Map<String, String?>,
)
