package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("medassist.public-data.drug-product")
data class DrugProductApiProperties(
    var baseUrl: String = "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07",
    var searchOperationPath: String = "/getDrugPrdtPrmsnInq07",
    var detailOperationPath: String = "/getDrugPrdtPrmsnDtlInq06",
    var ingredientOperationPath: String = "/getDrugPrdtMcpnDtlInq07",
    var pageSize: Int = 20,
    var maximumPages: Int = 50,
    var mapping: Mapping = Mapping(),
    var client: PublicDataClientPolicy = PublicDataClientPolicy(),
) {
    data class Mapping(
        var searchItemsJsonPointer: String = "/body/items",
        var ingredientItemsJsonPointer: String = "/body/items",
        var totalCountJsonPointer: String = "/body/totalCount",
        var pageNumberJsonPointer: String = "/body/pageNo",
        var pageSizeJsonPointer: String = "/body/numOfRows",
        var productCodeField: String = "ITEM_SEQ",
        var productNameField: String = "ITEM_NAME",
        var manufacturerField: String = "ENTP_NAME",
        var ingredientProductNameParameter: String = "Prduct",
        var ingredientProductCodeField: String = "ITEM_SEQ",
        var ingredientProductNameField: String = "PRDUCT",
        var ingredientSequenceField: String = "MTRAL_SN",
        var ingredientAmountSequenceField: String = "TAMT_SEQ",
        var ingredientCodeField: String = "MTRAL_CODE",
        var ingredientDisplayNameField: String = "MTRAL_NM",
        var ingredientKoreanNameField: String = "MTRAL_NM",
        var ingredientEnglishNameField: String = "MAIN_INGR_ENG",
        var ingredientAmountField: String = "QNT",
        var ingredientUnitField: String = "INGD_UNIT_CD",
    ) {
        fun searchIsConfigured(): Boolean =
            searchItemsJsonPointer.isNotBlank() &&
                productCodeField.isNotBlank() &&
                productNameField.isNotBlank()

        fun ingredientsAreConfigured(): Boolean =
            ingredientItemsJsonPointer.isNotBlank() &&
                totalCountJsonPointer.isNotBlank() &&
                pageNumberJsonPointer.isNotBlank() &&
                pageSizeJsonPointer.isNotBlank() &&
                ingredientProductNameParameter.isNotBlank() &&
                ingredientProductCodeField.isNotBlank() &&
                ingredientDisplayNameField.isNotBlank()
    }
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
