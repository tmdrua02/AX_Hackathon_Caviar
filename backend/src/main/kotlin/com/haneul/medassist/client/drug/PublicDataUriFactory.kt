package com.haneul.medassist.client.drug

import com.haneul.medassist.client.common.ServiceKeyEncoder
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
    private val serviceKeyEncoder: ServiceKeyEncoder,
) {
    fun searchUri(query: String): URI = baseBuilder(properties.searchOperationPath)
        .queryParam("serviceKey", serviceKeyEncoder.encodedQueryValue())
        .queryParam("pageNo", 1)
        .queryParam("numOfRows", properties.pageSize)
        .queryParam("type", "json")
        .queryParam("item_name", encodeQueryValue(query))
        .build(true)
        .toUri()

    fun ingredientUri(productName: String, pageNumber: Int): URI {
        val parameter = properties.mapping.ingredientProductNameParameter
        if (parameter.isBlank()) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "공공데이터포털 Swagger 명세 확인 필요: 주성분 조회의 제품명 요청 파라미터가 설정되지 않았습니다.",
            )
        }
        return baseBuilder(properties.ingredientOperationPath)
            .queryParam("serviceKey", serviceKeyEncoder.encodedQueryValue())
            .queryParam("pageNo", pageNumber)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("type", "json")
            .queryParam(parameter, encodeQueryValue(productName))
            .build(true)
            .toUri()
    }

    fun detailUri(productCode: String, pageNumber: Int = 1): URI = baseBuilder(properties.detailOperationPath)
        .queryParam("serviceKey", serviceKeyEncoder.encodedQueryValue())
        .queryParam("pageNo", pageNumber)
        .queryParam("numOfRows", properties.pageSize)
        .queryParam("type", "json")
        .queryParam("item_seq", encodeQueryValue(productCode))
        .build(true)
        .toUri()

    private fun baseBuilder(operationPath: String): UriComponentsBuilder =
        UriComponentsBuilder.fromUriString(properties.baseUrl).path(operationPath)

    private fun encodeQueryValue(value: String): String =
        // 서비스키의 '/', '+', '='도 percent-encode해야 게이트웨이에서 값 경계가 보존된다.
        UriUtils.encode(value, StandardCharsets.UTF_8)

}
