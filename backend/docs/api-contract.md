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
