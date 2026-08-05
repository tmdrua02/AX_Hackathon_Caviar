package com.haneul.medassist.client.drug

import com.haneul.medassist.config.DrugProductApiProperties
import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.SourceMetadata
import com.haneul.medassist.domain.medication.VerifiedDrugProduct
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import com.haneul.medassist.service.IngredientNormalizer
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.time.Instant

@Component
class DrugProductApiMapper(
    private val properties: DrugProductApiProperties,
    private val ingredientNormalizer: IngredientNormalizer,
) {
    fun requireSearchMapping() {
        if (!properties.mapping.searchIsConfigured()) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "공공데이터포털 Swagger 명세 확인 필요: 제품 코드·제품명·items 필드 매핑이 설정되지 않았습니다.",
            )
        }
    }

    fun toProduct(record: JsonNode, retrievedAt: Instant): VerifiedDrugProduct {
        val productCode = requiredText(record, properties.mapping.productCodeField, "품목기준코드")
        val productName = requiredText(record, properties.mapping.productNameField, "제품명")
        val manufacturer = optionalText(record, properties.mapping.manufacturerField)
        return VerifiedDrugProduct(
            productCode = productCode,
            productName = productName,
            manufacturer = manufacturer,
            source = source(productCode, retrievedAt),
        )
    }

    fun toIngredient(
        record: JsonNode,
        productCode: String,
        retrievedAt: Instant,
    ): Ingredient {
        val displayName = requiredText(
            record,
            properties.mapping.ingredientDisplayNameField,
            "주성분 표시명",
        )
        val amountText = optionalText(record, properties.mapping.ingredientAmountField)
        val amount = amountText?.toBigDecimalOrNull()
        if (amountText != null && amount == null) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                "공공 API 성분 함량을 숫자로 변환할 수 없습니다.",
            )
        }
        return ingredientNormalizer.normalize(
            providerCode = optionalText(record, properties.mapping.ingredientCodeField),
            displayName = displayName,
            koreanName = optionalText(record, properties.mapping.ingredientKoreanNameField),
            englishName = optionalText(record, properties.mapping.ingredientEnglishNameField),
            amount = amount,
            unit = optionalText(record, properties.mapping.ingredientUnitField),
            source = source(productCode, retrievedAt),
        )
    }

    private fun source(recordId: String, retrievedAt: Instant): SourceMetadata = SourceMetadata(
        name = SOURCE_NAME,
        recordId = recordId,
        retrievedAt = retrievedAt,
        providerReference = SOURCE_REFERENCE,
    )

    private fun requiredText(record: JsonNode, field: String, description: String): String =
        optionalText(record, field) ?: throw PublicDataApiException(
            ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
            "공공 API 레코드에 필수 $description 필드가 없습니다.",
        )

    private fun optionalText(record: JsonNode, field: String): String? {
        if (field.isBlank()) return null
        return record.get(field)
            ?.takeUnless { it.isNull || it.isMissingNode }
            ?.asString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    companion object {
        const val SOURCE_NAME = "식품의약품안전처 의약품 제품 허가정보"
        const val SOURCE_REFERENCE = "https://www.data.go.kr/data/15095677/openapi.do"
    }
}
