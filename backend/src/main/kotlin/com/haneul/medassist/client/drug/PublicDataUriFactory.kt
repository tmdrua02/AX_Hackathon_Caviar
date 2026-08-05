package com.haneul.medassist.client.drug

import com.haneul.medassist.config.DrugProductApiProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

@Component
class PublicDataUriFactory(
    private val properties: DrugProductApiProperties,
) {
    fun searchUri(query: String): URI = baseBuilder(properties.searchOperationPath)
        .queryParam("serviceKey", encodeQueryValue(decodedServiceKey()))
        .queryParam("pageNo", 1)
        .queryParam("numOfRows", properties.pageSize)
        .queryParam("type", "json")
        .queryParam("item_name", encodeQueryValue(query))
        .build(true)
        .toUri()

    fun ingredientUri(productCode: String): URI {
        val parameter = properties.mapping.ingredientProductCodeParameter
        if (parameter.isBlank()) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "공공데이터포털 Swagger 명세 확인 필요: 주성분 조회의 품목코드 요청 파라미터가 설정되지 않았습니다.",
            )
        }
        return baseBuilder(properties.ingredientOperationPath)
            .queryParam("serviceKey", encodeQueryValue(decodedServiceKey()))
            .queryParam("pageNo", 1)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("type", "json")
            .queryParam(parameter, encodeQueryValue(productCode))
            .build(true)
            .toUri()
    }

    private fun baseBuilder(operationPath: String): UriComponentsBuilder =
        UriComponentsBuilder.fromUriString(properties.baseUrl).path(operationPath)

    private fun encodeQueryValue(value: String): String =
        // 서비스키의 '/', '+', '='도 percent-encode해야 게이트웨이에서 값 경계가 보존된다.
        UriUtils.encode(value, StandardCharsets.UTF_8)

    private fun decodedServiceKey(): String {
        val key = properties.serviceKey.trim()
        if (key.isBlank()) {
            throw PublicDataApiException(ApiErrorCode.PUBLIC_API_NOT_CONFIGURED)
        }
        return if (properties.serviceKeyEncoded) {
            // UriUtils는 percent escape만 해제하므로 URLDecoder와 달리 '+'를 공백으로 바꾸지 않는다.
            UriUtils.decode(key, StandardCharsets.UTF_8)
        } else {
            key
        }
    }
}
