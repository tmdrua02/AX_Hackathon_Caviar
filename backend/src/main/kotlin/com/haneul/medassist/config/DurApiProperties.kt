package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("medassist.public-data.dur")
data class DurApiProperties(
    var baseUrl: String = "https://apis.data.go.kr/1471000/DURIrdntInfoService03",
    var operationPath: String = "/getUsjntTabooInfoList02",
    var responseType: String = "json",
    var typeName: String = "병용금기",
    var pageSize: Int = 100,
    var maxPages: Int = 100,
    var maxRecords: Int = 10_000,
    var client: PublicDataClientPolicy = PublicDataClientPolicy(),
)
