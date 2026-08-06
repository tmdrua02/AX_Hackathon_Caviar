package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class DurProviderResponseMapper(
    private val responseParser: RawPublicDataResponseParser,
) {
    fun map(root: JsonNode): DurProviderResponse {
        val header = DurProviderHeader(
            resultCode = requiredText(root.at("/header/resultCode"), "resultCode"),
            resultMsg = requiredText(root.at("/header/resultMsg"), "resultMsg"),
        )
        val metadata = responseParser.pageMetadata(
            root = root,
            totalCountJsonPointer = "/body/totalCount",
            pageNumberJsonPointer = "/body/pageNo",
            pageSizeJsonPointer = "/body/numOfRows",
        )
        val items = itemWrappers(root.at("/body/items"))
        if (metadata.totalCount > 0 && items.isEmpty()) {
            throw invalid("DUR 응답 totalCount와 items가 일치하지 않습니다.")
        }
        return DurProviderResponse(
            header = header,
            body = DurProviderBody(
                pageNo = metadata.pageNumber,
                totalCount = metadata.totalCount,
                numOfRows = metadata.pageSize,
                items = items,
            ),
        )
    }

    private fun itemWrappers(node: JsonNode): List<DurProviderItemWrapper> {
        if (node.isMissingNode || node.isNull) return emptyList()
        val wrappers = when {
            node.isArray -> node.toList()
            node.isObject -> listOf(node)
            else -> throw invalid("DUR 응답 items가 배열 또는 객체가 아닙니다.")
        }
        return wrappers.map { wrapper ->
            val itemNode = wrapper.get("item")
                ?.takeIf { it.isObject }
                ?: throw invalid("DUR 응답 item wrapper 구조를 확인할 수 없습니다.")
            DurProviderItemWrapper(mapItem(itemNode))
        }
    }

    private fun mapItem(node: JsonNode): DurProviderItem = DurProviderItem(
        typeName = text(node, "TYPE_NAME"),
        mixType = text(node, "MIX_TYPE"),
        ingredientCode = text(node, "INGR_CODE"),
        ingredientEnglishName = text(node, "INGR_ENG_NAME"),
        ingredientKoreanName = text(node, "INGR_KOR_NAME"),
        mix = text(node, "MIX"),
        originalIngredientText = text(node, "ORI"),
        classification = text(node, "CLASS"),
        mixtureMixType = text(node, "MIXTURE_MIX_TYPE"),
        relatedIngredientCode = text(node, "MIXTURE_INGR_CODE"),
        relatedIngredientEnglishName = text(node, "MIXTURE_INGR_ENG_NAME"),
        relatedIngredientKoreanName = text(node, "MIXTURE_INGR_KOR_NAME"),
        mixtureMix = text(node, "MIXTURE_MIX"),
        mixtureOriginalText = text(node, "MIXTURE_ORI"),
        mixtureClassification = text(node, "MIXTURE_CLASS"),
        notificationDate = text(node, "NOTIFICATION_DATE"),
        prohibitionContent = text(node, "PROHBT_CONTENT"),
        remark = text(node, "REMARK"),
        deletionStatus = text(node, "DEL_YN"),
        rawFields = node.properties().associate { it.key to nullableRawText(it.value) },
    )

    private fun text(node: JsonNode, fieldName: String): String? =
        node.get(fieldName)?.let(::nullableRawText)?.trim()?.takeIf { it.isNotEmpty() }

    private fun nullableRawText(node: JsonNode): String? =
        node.takeUnless { it.isNull || it.isMissingNode }?.asString()

    private fun requiredText(node: JsonNode, fieldName: String): String =
        nullableRawText(node)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw invalid("DUR 응답에 $fieldName 값이 없습니다.")

    private fun invalid(message: String): PublicDataApiException =
        PublicDataApiException(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, message)
}
