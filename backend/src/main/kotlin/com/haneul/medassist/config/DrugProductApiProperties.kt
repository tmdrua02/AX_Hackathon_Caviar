package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("medassist.public-data.drug-product")
data class DrugProductApiProperties(
    var baseUrl: String = "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07",
    var serviceKey: String = "",
    var serviceKeyEncoded: Boolean = true,
    var searchOperationPath: String = "/getDrugPrdtPrmsnInq07",
    var ingredientOperationPath: String = "/getDrugPrdtMcpnDtlInq07",
    var pageSize: Int = 20,
    var mapping: Mapping = Mapping(),
    var policy: Policy = Policy(),
) {
    data class Mapping(
        var searchItemsJsonPointer: String = "",
        var ingredientItemsJsonPointer: String = "",
        var productCodeField: String = "",
        var productNameField: String = "",
        var manufacturerField: String = "",
        var ingredientProductCodeParameter: String = "",
        var ingredientCodeField: String = "",
        var ingredientDisplayNameField: String = "",
        var ingredientKoreanNameField: String = "",
        var ingredientEnglishNameField: String = "",
        var ingredientAmountField: String = "",
        var ingredientUnitField: String = "",
    ) {
        fun searchIsConfigured(): Boolean =
            searchItemsJsonPointer.isNotBlank() &&
                productCodeField.isNotBlank() &&
                productNameField.isNotBlank()

        fun ingredientsAreConfigured(): Boolean =
            ingredientItemsJsonPointer.isNotBlank() &&
                ingredientProductCodeParameter.isNotBlank() &&
                ingredientDisplayNameField.isNotBlank()
    }

    data class Policy(
        var connectTimeout: Duration = Duration.ofSeconds(2),
        var readTimeout: Duration = Duration.ofSeconds(5),
        var maxAttempts: Int = 2,
        var initialBackoff: Duration = Duration.ofMillis(300),
        var circuitFailureThreshold: Int = 5,
        var circuitOpenDuration: Duration = Duration.ofSeconds(30),
        var maxConcurrentCalls: Int = 8,
        var requestsPerSecond: Int = 5,
    )
}

@ConfigurationProperties("medassist.public-data.cache")
data class PublicDataCacheProperties(
    var positiveSearchTtl: Duration = Duration.ofHours(6),
    var negativeSearchTtl: Duration = Duration.ofMinutes(5),
    var ingredientTtl: Duration = Duration.ofHours(24),
    var maximumSize: Long = 1_000,
)

@ConfigurationProperties("medassist.matching")
data class MatchingProperties(
    var autoSelectionScore: Int = 95,
    var minimumScoreGap: Int = 10,
    var maximumCandidates: Int = 5,
)
