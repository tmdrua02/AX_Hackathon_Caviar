# MedAssist Backend

Kotlin, Spring Boot 4.1, Java 17 기반 의약품 검색 백엔드입니다.

## 현재 구현 범위

- `POST /api/v1/drug-products/search`
- Unicode NFKC, 공백, 제형, 용량, 단위, 제조사 힌트 정규화
- 공식 제품 후보 점수 계산 및 정렬
- 식약처 제품 허가정보 API용 `RestClient`
- JSON/XML raw 응답 수용 및 `resultCode` 검증
- Swagger로 확인된 제품·구조화 주성분 필드 기본 매핑
- `Prduct` 제품명 조회 후 모든 페이지의 `ITEM_SEQ` 정확 일치 검증
- 응답 원본 byte 기반 엄격한 UTF-8 디코딩
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

의약품 허가정보의 확인된 Swagger 매핑은 기본값으로 적용되어 있습니다. 다음 환경변수는 공급자 변경이나 검증 환경에서 기본값을 덮어쓸 때만 사용합니다.

```bash
export DRUG_API_SEARCH_ITEMS_JSON_POINTER='/body/items'
export DRUG_API_PRODUCT_CODE_FIELD='ITEM_SEQ'
export DRUG_API_PRODUCT_NAME_FIELD='ITEM_NAME'
export DRUG_API_MANUFACTURER_FIELD='ENTP_NAME'

export DRUG_API_INGREDIENT_ITEMS_JSON_POINTER='/body/items'
export DRUG_API_INGREDIENT_PRODUCT_NAME_PARAMETER='Prduct'
export DRUG_API_INGREDIENT_PRODUCT_CODE_FIELD='ITEM_SEQ'
export DRUG_API_INGREDIENT_CODE_FIELD='MTRAL_CODE'
export DRUG_API_INGREDIENT_DISPLAY_NAME_FIELD='MTRAL_NM'
export DRUG_API_INGREDIENT_KOREAN_NAME_FIELD='MTRAL_NM'
export DRUG_API_INGREDIENT_ENGLISH_NAME_FIELD='MAIN_INGR_ENG'
export DRUG_API_INGREDIENT_AMOUNT_FIELD='QNT'
export DRUG_API_INGREDIENT_UNIT_FIELD='INGD_UNIT_CD'
```

키가 없어도 서버는 시작되지만 실제 검색은 `PUBLIC_API_NOT_CONFIGURED` Problem Details로 거부됩니다. 매핑을 빈 값으로 덮어쓴 경우에는 `PUBLIC_API_MAPPING_UNVERIFIED`를 반환하며 가짜 후보를 만들지 않습니다.

인코딩된 서비스키는 설정 시 percent-decode한 뒤 각 query value를 정확히 한 번 percent-encode합니다. `%2F`가 `%252F`로 바뀌지 않는 회귀 테스트가 포함되어 있습니다. `+`도 공백으로 바꾸지 않습니다.

`DATA_GO_KR_SERVICE_KEY`와 `DATA_GO_KR_SERVICE_KEY_ENCODED`는 네 공공 API가 함께 쓰는 하나의 자격증명입니다. 환경변수 이름은 변경하지 않았습니다. 반면 endpoint, timeout, retry, rate limit, bulkhead, circuit breaker 정책과 그 실행 상태는 API별로 분리합니다. 현재는 제품 허가정보용 `drugProductCallExecutor`만 생성하며, DUR 성분정보와 건강기능식품 제품정보·품목제조신고 원재료정보의 실제 operation 및 클라이언트는 구현하지 않았습니다. 명세가 없는 operation path, 요청변수, 응답 필드는 추측하지 않습니다.

전체 data.go.kr 계정 호출량을 한 번 더 제한하는 전역 rate limiter는 현재 범위에 포함하지 않았습니다. 여러 API를 실제로 연결할 때 계정 quota 정책을 확인한 뒤 별도 계층으로 추가해야 합니다.

주성분 API는 품목코드 요청변수를 제공하지 않으므로 선택 제품의 정확한 `ITEM_NAME`을 `Prduct`로 요청합니다. 응답의 모든 페이지를 수집한 다음 `ITEM_SEQ`가 선택 제품의 품목기준코드와 같은 구조화 레코드만 사용합니다. `MATERIAL_NAME`, `MAIN_ITEM_INGR`, `MAIN_INGR_ENG`를 구분자로 나눠 새로운 공식 성분을 만들지 않습니다.

외부 응답은 `String` 변환 전에 byte 배열로 받고, JSON에 charset이 없으면 UTF-8을 엄격하게 적용합니다. 잘못된 byte 또는 replacement character `�`는 `PUBLIC_API_INVALID_RESPONSE`로 처리합니다. 실제 운영 응답의 `Content-Type` 헤더는 서비스키가 준비된 수동 통합 테스트에서 추가 실측해야 합니다.

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
