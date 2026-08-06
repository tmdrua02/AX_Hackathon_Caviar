package com.haneul.medassist.exception

import org.springframework.http.HttpStatus

enum class ApiErrorCode(
    val status: HttpStatus,
    val title: String,
) {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
    PUBLIC_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "공공 의약품 API가 설정되지 않았습니다."),
    PUBLIC_API_MAPPING_UNVERIFIED(HttpStatus.SERVICE_UNAVAILABLE, "공공 API 응답 매핑 확인이 필요합니다."),
    PUBLIC_API_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "공공 의약품 API 인증에 실패했습니다."),
    PUBLIC_API_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "공공 의약품 API 호출 한도를 초과했습니다."),
    PUBLIC_API_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "공공 의약품 API 응답이 지연되고 있습니다."),
    PUBLIC_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "공공 의약품 API를 사용할 수 없습니다."),
    PUBLIC_API_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "공공 의약품 API 응답을 확인할 수 없습니다."),
    PUBLIC_API_RESPONSE_MISMATCH(HttpStatus.BAD_GATEWAY, "공공 의약품 API 응답이 선택한 제품과 일치하지 않습니다."),
    PUBLIC_API_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "공공 의약품 API 보호 회로가 열려 있습니다."),
}
