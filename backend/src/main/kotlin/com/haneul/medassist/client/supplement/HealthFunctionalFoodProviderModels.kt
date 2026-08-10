package com.haneul.medassist.client.supplement

data class HealthFunctionalFoodProviderHeader(
    val resultCode: String,
    val resultMsg: String,
)

data class HealthFunctionalFoodProviderBody(
    val pageNo: Int,
    val totalCount: Int,
    val numOfRows: Int,
    val items: List<HealthFunctionalFoodProviderItem>,
)

data class HealthFunctionalFoodProviderResponse(
    val header: HealthFunctionalFoodProviderHeader,
    val body: HealthFunctionalFoodProviderBody,
)

data class HealthFunctionalFoodProviderItem(
    val manufacturer: String?,
    val productName: String?,
    val statementNo: String?,
    val registerDate: String?,
    val distributionPeriod: String?,
    val appearance: String?,
    val usage: String?,
    val storage: String?,
    val intakeHint: String?,
    val mainFunction: String?,
    val baseStandard: String?,
    val rawProviderRecord: Map<String, String?>,
)

data class HealthFunctionalFoodListQuery(
    val productName: String? = null,
    val manufacturer: String? = null,
    val statementNo: String? = null,
)
