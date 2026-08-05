package com.haneul.medassist.client.common

import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.xml.XmlMapper

@Component
class RawPublicDataResponseParser(
    private val objectMapper: ObjectMapper,
) {
    private val xmlMapper = XmlMapper()

    fun parse(body: String?): JsonNode {
        if (body.isNullOrBlank()) {
            throw PublicDataApiException(ApiErrorCode.PUBLIC_API_INVALID_RESPONSE, "공공 API 응답 본문이 비어 있습니다.")
        }
        return try {
            if (body.trimStart().startsWith("<")) xmlMapper.readTree(body) else objectMapper.readTree(body)
        } catch (exception: Exception) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                "공공 API 응답 형식을 읽을 수 없습니다.",
                exception,
            )
        }
    }

    fun records(root: JsonNode, jsonPointer: String): List<JsonNode> {
        if (jsonPointer.isBlank()) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "공공데이터포털 Swagger 명세 확인 필요: items JSON pointer가 설정되지 않았습니다.",
            )
        }
        val node = root.at(jsonPointer)
        if (node.isMissingNode || node.isNull) return emptyList()
        return when {
            node.isArray -> node.toList()
            node.isObject -> listOf(node)
            else -> throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                "설정한 items 위치가 배열 또는 객체가 아닙니다.",
            )
        }
    }
}
