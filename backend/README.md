# MedAssist Backend

Kotlin, Spring Boot 4.1, Java 17 기반 의약품 검색 백엔드입니다.

## 현재 구현 범위

- `POST /api/v1/drug-products/search`
- Unicode NFKC, 공백, 제형, 용량, 단위, 제조사 힌트 정규화
- 공식 제품 후보 점수 계산 및 정렬
- 식약처 제품 허가정보 API용 `RestClient`
- JSON/XML raw 응답 수용 및 `resultCode` 검증
- 제품·성분 필드의 Swagger 기반 설정 주입
- 공식 API 성공 빈 결과와 외부 API 실패 구분
- 후보별 주성분 조회 상태와 coverage
- Caffeine positive/negative/ingredient 캐시
- outbound timeout, retry, rate limit, bulkhead, 간단한 circuit breaker
- RFC Problem Details 오류 응답
- 동일 공식 성분코드 비교와 DUR 안전 상태 골격
- Android Retrofit 검색 계약

DUR 실제 호출과 DB 저장은 아직 구현하지 않았습니다. DUR 클라이언트는 명세가 확인되기 전 항상 `DUR_SCHEMA_UNVERIFIED`를 반환하므로 불완전 분석이 `NO_KNOWN_ISSUE`가 되지 않습니다.

## 실행

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew bootRun
```

확인:

```bash
curl http://localhost:8080/health
```

Android 에뮬레이터의 백엔드 주소는 `http://10.0.2.2:8080/`입니다.

## 공공데이터 환경변수

서비스키는 Android나 Git에 저장하지 않습니다.

```bash
export DATA_GO_KR_SERVICE_KEY='발급받은키'
export DATA_GO_KR_SERVICE_KEY_ENCODED='true'
```

현재 저장소에는 공식 Swagger 응답 샘플이 없으므로 다음 매핑은 의도적으로 기본값이 비어 있습니다. 공공데이터포털 Swagger에서 실제 필드명을 확인한 다음에만 설정합니다.

```bash
export DRUG_API_SEARCH_ITEMS_JSON_POINTER='/Swagger에서_확인한_배열_위치'
export DRUG_API_PRODUCT_CODE_FIELD='Swagger에서_확인한_품목코드_필드'
export DRUG_API_PRODUCT_NAME_FIELD='Swagger에서_확인한_제품명_필드'
export DRUG_API_MANUFACTURER_FIELD='Swagger에서_확인한_제조사_필드'

export DRUG_API_INGREDIENT_ITEMS_JSON_POINTER='/Swagger에서_확인한_성분_배열_위치'
export DRUG_API_INGREDIENT_PRODUCT_CODE_PARAMETER='Swagger에서_확인한_품목코드_요청변수'
export DRUG_API_INGREDIENT_CODE_FIELD='Swagger에서_확인한_성분코드_필드'
export DRUG_API_INGREDIENT_DISPLAY_NAME_FIELD='Swagger에서_확인한_성분명_필드'
export DRUG_API_INGREDIENT_KOREAN_NAME_FIELD='Swagger에서_확인한_한글명_필드'
export DRUG_API_INGREDIENT_ENGLISH_NAME_FIELD='Swagger에서_확인한_영문명_필드'
export DRUG_API_INGREDIENT_AMOUNT_FIELD='Swagger에서_확인한_함량_필드'
export DRUG_API_INGREDIENT_UNIT_FIELD='Swagger에서_확인한_단위_필드'
```

매핑 또는 키가 없더라도 서버는 시작됩니다. 검색 요청 시 `PUBLIC_API_NOT_CONFIGURED` 또는 `PUBLIC_API_MAPPING_UNVERIFIED` Problem Details를 반환하며 가짜 후보를 만들지 않습니다.

인코딩된 서비스키는 설정 시 percent-decode한 뒤 각 query value를 정확히 한 번 percent-encode합니다. `%2F`가 `%252F`로 바뀌지 않는 회귀 테스트가 포함되어 있습니다. `+`도 공백으로 바꾸지 않습니다.

## API 테스트

```bash
curl -i \
  -X POST \
  'http://localhost:8080/api/v1/drug-products/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "타이레놀 500"
  }'
```

빈 검색어:

```bash
curl -i \
  -X POST \
  'http://localhost:8080/api/v1/drug-products/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "   "
  }'
```

테스트와 빌드:

```bash
./gradlew test
./gradlew build
```

## 의료 안전 원칙

- LLM은 공식 후보, 제품 코드, 성분, DUR 결과, 근거를 만들 수 없습니다.
- 공식 제품 후보는 `DrugProductApiClient`의 HTTP 응답으로만 생성합니다.
- API 오류는 정상 빈 결과로 캐시하거나 반환하지 않습니다.
- 성분 조회 실패 후보에는 `ingredientLookupStatus`와 불완전 coverage를 반환합니다.
- 모든 DUR 성분 쌍이 성공해야만 `NO_KNOWN_ISSUE`가 가능합니다.
- 결과는 정보 제공용이며 의사·약사 상담 문구를 포함합니다.

## 가장 위험한 실수

1. 공공 API 실패를 검색 결과 없음으로 처리
2. 검색 결과 없음을 복용 가능으로 표현
3. LLM이 제품이나 성분 후보를 생성
4. 유사 제품명을 사용자 확인 없이 확정
5. API 키를 Android 또는 Git에 저장
6. 인코딩된 서비스키를 이중 인코딩
7. HTTP 200만 보고 `resultCode`를 검사하지 않음
8. 일부 성분 조회 실패인데 `NO_KNOWN_ISSUE` 반환
9. 복합제 일부 성분만 비교
10. 출처 레코드 ID와 조회 시각을 보존하지 않음

추가 문서:

- [아키텍처](docs/architecture.md)
- [API 계약](docs/api-contract.md)
- [공공 API 매핑 상태](docs/public-data-api-mapping.md)
- [보안 정책](docs/security.md)
- [DB 스키마 초안](docs/database-schema.sql)
