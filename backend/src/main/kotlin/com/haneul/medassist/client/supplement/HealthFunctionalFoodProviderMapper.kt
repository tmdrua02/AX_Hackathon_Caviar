package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class HealthFunctionalFoodProviderMapper(
    private val responseParser: RawPublicDataResponseParser,
) {
    fun map(root: JsonNode): HealthFunctionalFoodProviderResponse {
        val metadata = responseParser.pageMetadata(
            root,
            totalCountJsonPointer = "/body/totalCount",
            pageNumberJsonPointer = "/body/pageNo",
            pageSizeJsonPointer = "/body/numOfRows",
        )
        val items = items(root.at("/body/items"))
        if (metadata.totalCount == 0 && items.isNotEmpty()) throw invalid("0건 응답에 item이 존재합니다.")
        if (metadata.totalCount > 0 && items.isEmpty()) throw invalid("응답 totalCount와 items가 일치하지 않습니다.")

        return HealthFunctionalFoodProviderResponse(
            header = HealthFunctionalFoodProviderHeader(
                resultCode = requiredText(root.at("/header/resultCode"), "resultCode"),
                resultMsg = requiredText(root.at("/header/resultMsg"), "resultMsg"),
            ),
            body = HealthFunctionalFoodProviderBody(
                pageNo = metadata.pageNumber,
                totalCount = metadata.totalCount,
                numOfRows = metadata.pageSize,
                items = items,
            ),
        )
    }

    private fun items(node: JsonNode): List<HealthFunctionalFoodProviderItem> {
        if (node.isMissingNode || node.isNull) return emptyList()
        if (!node.isArray) throw invalid("items가 실제 확인된 배열 구조가 아닙니다.")
        return node.toList().map { wrapper ->
            val item = wrapper.get("item") ?: throw invalid("items 원소에 item wrapper가 없습니다.")
            if (!item.isObject) throw invalid("item이 객체가 아닙니다.")
            HealthFunctionalFoodProviderItem(
                manufacturer = text(item, "ENTRPS"),
                productName = text(item, "PRDUCT"),
                statementNo = text(item, "STTEMNT_NO"),
                registerDate = text(item, "REGIST_DT"),
                distributionPeriod = rawText(item, "DISTB_PD"),
                appearance = rawText(item, "SUNGSANG"),
                usage = rawText(item, "SRV_USE"),
                storage = rawText(item, "PRSRV_PD"),
                intakeHint = rawText(item, "INTAKE_HINT1"),
                mainFunction = rawText(item, "MAIN_FNCTN"),
                baseStandard = rawText(item, "BASE_STANDARD"),
                rawProviderRecord = item.properties().associate { entry ->
                    entry.key to entry.value.takeUnless { it.isNull || it.isMissingNode }?.asString()
                },
            )
        }
    }

    private fun text(node: JsonNode, fieldName: String): String? =
        node.get(fieldName)?.takeUnless { it.isNull || it.isMissingNode }?.asString()?.trim()?.takeIf(String::isNotEmpty)

    private fun rawText(node: JsonNode, fieldName: String): String? =
        node.get(fieldName)?.takeUnless { it.isNull || it.isMissingNode }?.asString()?.takeIf(String::isNotBlank)

    private fun requiredText(node: JsonNode, fieldName: String): String =
        node.takeUnless { it.isNull || it.isMissingNode }?.asString()?.trim()?.takeIf(String::isNotEmpty)
            ?: throw invalid("응답에 $fieldName 값이 없습니다.")

    private fun invalid(detail: String) = PublicDataApiException(
        ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
        "건강기능식품 $detail",
    )
}
