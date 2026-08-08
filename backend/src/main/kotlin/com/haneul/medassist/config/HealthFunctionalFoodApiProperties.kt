package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("medassist.public-data.health-functional-food")
data class HealthFunctionalFoodApiProperties(
    var baseUrl: String = "https://apis.data.go.kr/1471000/HtfsInfoService03",
    var listOperationPath: String = "/getHtfsList01",
    var detailOperationPath: String = "/getHtfsItem01",
    var responseType: String = "json",
    var productNameParameter: String = "Prduct",
    var manufacturerParameter: String = "Entrps",
    var listStatementNoParameter: String = "Sttemnt_no",
    var detailStatementNoParameter: String = "STTEMNT_NO",
    var pageSize: Int = 100,
    var maxPages: Int = 100,
    var maxRecords: Int = 10_000,
    var client: PublicDataClientPolicy = PublicDataClientPolicy(),
)
