package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.RawPublicDataResponseParser
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class DrugOverviewResponseMapper(
    private val responseParser: RawPublicDataResponseParser,
) {
    fun map(root: JsonNode): DrugOverviewProviderResponse {
        val header = DrugOverviewProviderHeader(
            resultCode = requiredText(root.at("/header/resultCode"), "resultCode"),
            resultMsg = requiredText(root.at("/header/resultMsg"), "resultMsg"),
        )
        val metadata = responseParser.pageMetadata(
            root,
            totalCountJsonPointer = "/body/totalCount",
            pageNumberJsonPointer = "/body/pageNo",
            pageSizeJsonPointer = "/body/numOfRows",
        )
        val items = items(root.at("/body/items"))
        if (metadata.totalCount > 0 && items.isEmpty()) {
            throw invalid("e약은요 응답 totalCount와 items가 일치하지 않습니다.")
        }
        return DrugOverviewProviderResponse(
            header,
            DrugOverviewProviderBody(
                pageNo = metadata.pageNumber,
                totalCount = metadata.totalCount,
                numOfRows = metadata.pageSize,
                items = items,
            ),
        )
    }

    private fun items(node: JsonNode): List<DrugOverviewProviderItem> {
        if (node.isMissingNode || node.isNull) return emptyList()
        val records = when {
            node.isArray -> node.toList()
            node.isObject -> listOf(node)
            else -> throw invalid("e약은요 응답 items가 배열 또는 객체가 아닙니다.")
        }
        return records.map { record ->
            if (!record.isObject) throw invalid("e약은요 item 구조를 확인할 수 없습니다.")
            DrugOverviewProviderItem(
                manufacturer = text(record, "entpName"),
                productName = text(record, "itemName"),
                productCode = text(record, "itemSeq"),
                efficacy = rawText(record, "efcyQesitm"),
                usageMethod = rawText(record, "useMethodQesitm"),
                warning = rawText(record, "atpnWarnQesitm"),
                precautions = rawText(record, "atpnQesitm"),
                interactions = rawText(record, "intrcQesitm"),
                sideEffects = rawText(record, "seQesitm"),
                storageMethod = rawText(record, "depositMethodQesitm"),
                openDate = text(record, "openDe"),
                updateDate = text(record, "updateDe"),
                imageUrl = text(record, "itemImage"),
            )
        }
    }

    private fun text(node: JsonNode, fieldName: String): String? =
        node.get(fieldName)?.takeUnless { it.isNull || it.isMissingNode }?.asString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun rawText(node: JsonNode, fieldName: String): String? =
        node.get(fieldName)?.takeUnless { it.isNull || it.isMissingNode }?.asString()?.takeIf { it.isNotBlank() }

    private fun requiredText(node: JsonNode, fieldName: String): String =
        node.takeUnless { it.isNull || it.isMissingNode }?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw invalid("e약은요 응답에 $fieldName 값이 없습니다.")

    private fun invalid(message: String) =
        PublicDataApiException(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, message)
}
