package com.haneul.medassist.client.dur

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DurApiProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

@Component
class DurPublicDataUriFactory(
    private val properties: DurApiProperties,
    private val serviceKeyEncoder: ServiceKeyEncoder,
) {
    fun lookupUri(request: DurLookupRequest, pageNumber: Int): URI {
        if (request.ingredientCode.isBlank() || pageNumber <= 0) {
            throw PublicDataApiException(
                ApiErrorCode.VALIDATION_FAILED,
                "DUR 조회 성분코드와 페이지 번호를 확인할 수 없습니다.",
            )
        }
        if (properties.typeName.isBlank() || properties.responseType.isBlank() || properties.pageSize <= 0) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "DUR API 요청 매핑 설정을 확인할 수 없습니다.",
            )
        }

        val builder = UriComponentsBuilder.fromUriString(properties.baseUrl)
            .path(properties.operationPath)
            .queryParam("serviceKey", serviceKeyEncoder.encodedQueryValue())
            .queryParam("pageNo", pageNumber)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("type", encodeQueryValue(properties.responseType))
            .queryParam("typeName", encodeQueryValue(properties.typeName))
            .queryParam("ingrCode", encodeQueryValue(request.ingredientCode.trim()))

        request.ingredientKoreanName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.queryParam("ingrKorName", encodeQueryValue(it))
        }
        return builder.build(true).toUri()
    }

    private fun encodeQueryValue(value: String): String = UriUtils.encode(value, StandardCharsets.UTF_8)
}
