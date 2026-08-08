package com.haneul.medassist.client.supplement

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.HealthFunctionalFoodApiProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

@Component
class HealthFunctionalFoodUriFactory(
    private val properties: HealthFunctionalFoodApiProperties,
    private val serviceKeyEncoder: ServiceKeyEncoder,
) {
    fun listUri(query: HealthFunctionalFoodListQuery, pageNumber: Int): URI {
        val builder = baseBuilder(properties.listOperationPath, pageNumber)
        query.productName?.trim()?.takeIf(String::isNotEmpty)?.let {
            builder.queryParam(properties.productNameParameter, encode(it))
        }
        query.manufacturer?.trim()?.takeIf(String::isNotEmpty)?.let {
            builder.queryParam(properties.manufacturerParameter, encode(it))
        }
        query.statementNo?.trim()?.takeIf(String::isNotEmpty)?.let {
            builder.queryParam(properties.listStatementNoParameter, encode(it))
        }
        return builder.build(true).toUri()
    }

    fun detailUri(statementNo: String, pageNumber: Int): URI {
        if (statementNo.isBlank()) throw invalidConfiguration()
        return baseBuilder(properties.detailOperationPath, pageNumber)
            .queryParam(properties.detailStatementNoParameter, encode(statementNo.trim()))
            .build(true)
            .toUri()
    }

    private fun baseBuilder(operationPath: String, pageNumber: Int): UriComponentsBuilder {
        if (
            operationPath.isBlank() || pageNumber <= 0 || properties.pageSize <= 0 ||
            properties.responseType.isBlank() || properties.productNameParameter.isBlank() ||
            properties.manufacturerParameter.isBlank() || properties.listStatementNoParameter.isBlank() ||
            properties.detailStatementNoParameter.isBlank()
        ) {
            throw invalidConfiguration()
        }
        return UriComponentsBuilder.fromUriString(properties.baseUrl)
            .path(operationPath)
            .queryParam("ServiceKey", serviceKeyEncoder.encodedQueryValue())
            .queryParam("pageNo", pageNumber)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("type", encode(properties.responseType))
    }

    private fun invalidConfiguration() = PublicDataApiException(
        ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
        "건강기능식품 API 요청 설정을 확인할 수 없습니다.",
    )

    private fun encode(value: String): String = UriUtils.encode(value, StandardCharsets.UTF_8)
}
