package com.haneul.medassist.client.drug.overview

data class DrugOverviewProviderResponse(
    val header: DrugOverviewProviderHeader,
    val body: DrugOverviewProviderBody,
)

data class DrugOverviewProviderHeader(
    val resultCode: String,
    val resultMsg: String,
)

data class DrugOverviewProviderBody(
    val pageNo: Int,
    val totalCount: Int,
    val numOfRows: Int,
    val items: List<DrugOverviewProviderItem>,
)

data class DrugOverviewProviderItem(
    val manufacturer: String?,
    val productName: String?,
    val productCode: String?,
    val efficacy: String?,
    val usageMethod: String?,
    val warning: String?,
    val precautions: String?,
    val interactions: String?,
    val sideEffects: String?,
    val storageMethod: String?,
    val openDate: String?,
    val updateDate: String?,
    val imageUrl: String?,
)
