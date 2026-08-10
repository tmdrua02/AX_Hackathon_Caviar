package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("medassist.public-data.drug-overview")
data class DrugOverviewApiProperties(
    var baseUrl: String = "https://apis.data.go.kr/1471000/DrbEasyDrugInfoService",
    var operationPath: String = "/getDrbEasyDrugList",
    var responseType: String = "json",
    var pageSize: Int = 100,
    var maxPages: Int = 100,
    var maxRecords: Int = 10_000,
    var client: PublicDataClientPolicy = PublicDataClientPolicy(),
)
