package com.haneul.medassist.client.common

import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class PublicDataApiResponseValidator {
    fun validate(root: JsonNode) {
        val resultCode = findCaseInsensitive(root, "resultCode")?.asString()?.trim()
            ?: throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                "공공 API 응답에 resultCode가 없습니다.",
            )
        if (resultCode == SUCCESS_RESULT_CODE) return

        val safeMessage = findCaseInsensitive(root, "resultMsg")?.asString()?.trim().orEmpty()
        val normalized = safeMessage.uppercase()
        when {
            normalized.contains("AUTH") || normalized.contains("KEY") || normalized.contains("인증") ->
                throw PublicDataApiException(ApiErrorCode.PUBLIC_API_AUTH_FAILED)

            normalized.contains("QUOTA") || normalized.contains("LIMIT") || normalized.contains("TRAFFIC") ||
                normalized.contains("한도") ->
                throw PublicDataApiException(
                    ApiErrorCode.PUBLIC_API_QUOTA_EXCEEDED,
                    retryable = false,
                )

            else -> throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_INVALID_RESPONSE,
                "공공 API가 성공하지 않은 결과 코드를 반환했습니다.",
            )
        }
    }

    private fun findCaseInsensitive(node: JsonNode, fieldName: String): JsonNode? {
        if (node.isObject) {
            node.properties().firstOrNull { it.key.equals(fieldName, ignoreCase = true) }?.let { return it.value }
            node.values().forEach { child -> findCaseInsensitive(child, fieldName)?.let { return it } }
        } else if (node.isArray) {
            node.forEach { child -> findCaseInsensitive(child, fieldName)?.let { return it } }
        }
        return null
    }

    companion object {
        // 공공데이터포털 게이트웨이의 일반 성공 코드. 실제 서비스 Swagger와 수동 통합 테스트에서 재확인한다.
        private const val SUCCESS_RESULT_CODE = "00"
    }
}
