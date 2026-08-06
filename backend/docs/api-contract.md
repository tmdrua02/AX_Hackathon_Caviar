# API Contract

## `POST /api/v1/drug-products/search`

요청:

```json
{"query":"타이레놀 500"}
```

성공 응답은 `query`, `normalizedQuery`, 공식 `candidates`, `requiresUserConfirmation`, `coverage`, `disclaimer`를 반환합니다. 각 후보에는 `matchReasons`와 `ingredientLookupStatus`가 추가되어 있습니다. Android의 kotlinx serialization은 알 수 없는 필드를 무시하도록 설정되어 있습니다.

정상적인 공식 검색 결과 0건은 HTTP 200과 빈 `candidates`입니다. 외부 API 실패는 아래 Problem Details이며 빈 성공 응답으로 위장하지 않습니다.

```json
{
  "type": "https://medassist.local/problems/public-api-unavailable",
  "title": "공공 의약품 API를 사용할 수 없습니다.",
  "status": 503,
  "detail": "공공 의약품 API를 사용할 수 없습니다.",
  "instance": "/api/v1/drug-products/search",
  "code": "PUBLIC_API_UNAVAILABLE",
  "timestamp": "ISO-8601"
}
```

## 기존 Android 계약

다음 백엔드 API는 Android에 이미 선언되어 있으나 새 `backend`에는 아직 구현되지 않았습니다.

- `POST /api/v1/prescription-drafts`
- `PATCH /api/v1/prescription-drafts/{id}`
- `POST /api/v1/prescription-drafts/{id}/confirm`
- `POST /api/v1/interaction-checks`
- `GET /api/v1/interaction-checks/{id}`

구현 시 Android `Models.kt`의 enum과 nullability를 그대로 유지합니다. 상호작용 결과에는 항상 coverage와 상담 안내를 포함하며 부분 성공은 `UNKNOWN`으로 처리합니다.

## `POST /api/v1/supplement-interaction-checks`

기존 endpoint를 변경하지 않고 추가한 약–건강기능식품 분석 계약입니다. 요청에는 사용자가 후보 확인을 끝낸 공식 식별값만 전달합니다.

```json
{
  "medicationProductCode": "공식 ITEM_SEQ",
  "supplementStatementNo": "공식 STTEMNT_NO"
}
```

응답은 `processingStatus`, immutable `severity`, 공식 제품 snapshot, 공식 약 성분, 검수 canonical 원료, 평가 pair, 일치 rule, 원문 evidence, coverage, `failedSteps`, 상담 disclaimer와 분석 시각을 포함합니다.

- `AVOID_COMBINATION`: VERIFIED 회피 규칙이 하나 이상 존재
- `CAUTION`: AVOID가 없고 VERIFIED 주의 규칙이 하나 이상 존재
- `NO_VERIFIED_RULE_FOUND`: 모든 식별·매핑·pair·repository coverage가 완전하지만 일치 VERIFIED 규칙이 없음
- `UNKNOWN`: 제품/성분/매핑/repository/pair coverage가 하나라도 불완전

`NO_VERIFIED_RULE_FOUND`는 병용 가능 또는 안전을 의미하지 않습니다. 형식이 잘못되거나 빈 코드는 기존 RFC Problem Details `VALIDATION_FAILED`를 반환합니다. 구문상 유효하지만 provider에서 찾지 못한 공식 코드는 HTTP 200의 `UNKNOWN`과 구체적인 `failedSteps`로 반환해 provider 정상 미검색과 transport 오류를 구분합니다.
