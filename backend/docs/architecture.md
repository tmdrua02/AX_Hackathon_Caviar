# Architecture

## 신뢰 경계

```mermaid
flowchart LR
    A["Android 앱"] --> O["ML Kit OCR"]
    A --> B["Spring Boot API"]
    O --> B
    B --> N["DrugNameNormalizer"]
    N --> M["DrugProductMatcher"]
    M --> P["식약처 제품 허가정보 API"]
    M --> C["Caffeine 캐시"]
    B --> I["성분 비교 서비스"]
    I --> D["DUR Client · 현재 명세 미확인"]
    L["선택적 LLM · 현재 비활성"] -. "검색어 힌트만" .-> N
    L -. "제품·성분·판정 생성 금지" .-> B
```

LLM 경계는 현재 구현하지 않았습니다. 추후 추가하더라도 출력 타입은 검색 문자열 힌트로 제한하고 `VerifiedDrugProduct`나 `Ingredient`를 생성할 수 없게 분리합니다.

## 제품 검색 흐름

```mermaid
sequenceDiagram
    participant A as Android
    participant B as Spring Boot
    participant N as Normalizer
    participant C as Cache
    participant P as 식약처 API

    A->>B: POST /api/v1/drug-products/search
    B->>N: 이름·용량·제형 분리
    B->>C: 정규화 검색어 조회
    alt cache miss
        B->>P: 공식 제품 목록 요청
        P-->>B: raw JSON 또는 XML
        B->>B: resultCode 및 Swagger 매핑 검증
        B->>C: 성공 결과만 캐시
    end
    B->>P: 상위 후보 주성분 조회
    P-->>B: 성분 또는 후보별 오류 상태
    B-->>A: 공식 후보·점수·coverage·출처
```

## 캐시

- 검색 성공: 6시간
- 검색 성공 빈 결과: 5분
- 성분 성공: 24시간
- 인증·quota·timeout·파싱 오류: 캐시하지 않음
- 운영 버전에서는 `DrugProductCache` 경계를 Redis 구현으로 교체 가능

## 비동기 분석 방향

현재는 동일 성분 비교 도메인까지만 구현했습니다. `POST /interaction-checks`를 구현할 때는 `PENDING` 저장 → 제한된 executor → `COMPLETED/PARTIAL/FAILED` 저장 → GET polling 순서로 구성합니다. JVM 재시작에도 작업이 보존되어야 하는 운영 버전에서는 DB outbox와 메시지 큐를 사용합니다.
