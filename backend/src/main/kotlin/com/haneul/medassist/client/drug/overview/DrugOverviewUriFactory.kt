package com.haneul.medassist.client.drug.overview

import com.haneul.medassist.client.common.ServiceKeyEncoder
import com.haneul.medassist.config.DrugOverviewApiProperties
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.PublicDataApiException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

@Component
class DrugOverviewUriFactory(
    private val properties: DrugOverviewApiProperties,
    private val serviceKeyEncoder: ServiceKeyEncoder,
) {
    fun lookupUri(query: DrugOverviewProviderQuery, pageNumber: Int): URI {
        if (pageNumber <= 0 || properties.pageSize <= 0 || properties.responseType.isBlank()) {
            throw PublicDataApiException(
                ApiErrorCode.PUBLIC_API_MAPPING_UNVERIFIED,
                "e약은요 요청 설정을 확인할 수 없습니다.",
            )
        }
        val builder = UriComponentsBuilder.fromUriString(properties.baseUrl)
            .path(properties.operationPath)
            .queryParam("ServiceKey", serviceKeyEncoder.encodedQueryValue())
            .queryParam("pageNo", pageNumber)
            .queryParam("numOfRows", properties.pageSize)

        query.manufacturer?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.queryParam("entpName", encode(it))
        }
        query.itemName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.queryParam("itemName", encode(it))
        }
        query.itemSeq?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.queryParam("itemSeq", encode(it))
        }
        return builder
            .queryParam("type", encode(properties.responseType))
            .build(true)
            .toUri()
    }

    private fun encode(value: String): String = UriUtils.encode(value, StandardCharsets.UTF_8)
}
