# MedAssist Backend

Kotlin, Spring Boot 4.1, Java 17 기반 의약품 검색 백엔드입니다.

## 현재 구현 범위

- `POST /api/v1/drug-products/search`
- `POST /api/v1/drug-interaction-checks` 다중 약–약 공식 성분/DUR 분석
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
- 식약처 DUR 병용금기 API 전용 client, URI factory, pagination, provider record 매핑
- DUR 전용 timeout, retry, bulkhead, circuit breaker와 opt-in 외부 통합 테스트
- 식약처 의약품개요정보(e약은요) 정확 품목 조회, 제품명 fallback, 전체 pagination
- e약은요 전용 timeout, retry, bulkhead, circuit breaker와 positive/negative 캐시
- 공식 근거의 원문 HTML과 의미를 바꾸지 않은 표시용 텍스트 분리
- 건강기능식품 `STTEMNT_NO` 탐색을 위한 교체 가능한 메모리 검색 인덱스와 REST 후보 검색
- 건강기능식품 목록·상세 provider, pagination, strict UTF-8, 전용 executor와 캐시
- 검수 출처·canonical 기능성 원료·제품 매핑·약 성분 상호작용 규칙 catalog
- 약–건강기능식품 Cartesian pair 판정, evidence, coverage와 additive REST endpoint
- Android Retrofit 검색 계약

DUR 병용금기 provider는 `IngredientComparisonService`의 공식 약 성분쌍 판정에 연결했습니다. A→B와 B→A를 모두 전체 pagination으로 조회하며, 어느 방향이든 ACTIVE 병용금기 근거가 확인되면 위험을 보존합니다. 양방향이 모두 complete일 때만 관계 없음 후보가 될 수 있고 일부 실패는 `NO_KNOWN_ISSUE`로 승격하지 않습니다. 건강기능식품 제품·원료는 이 경로에 전달하지 않습니다.

e약은요는 공급실적이 있는 일반의약품 중심의 보조 설명 데이터입니다. 조회 실패나 정상 미제공 결과를 제품 부재 또는 효능 부재로 해석하지 않으며, 기존 `Medication`과 공식 성분을 변경하지 않습니다. 건강기능식품 provider는 공식 제품 기본정보만 반환하며 원재료 또는 상호작용 근거로 사용하지 않습니다. 검색은 provider를 우선하고 정상 `NOT_FOUND`일 때만 기존 index를 fallback으로 사용합니다.

## 실행

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew bootRun
```

확인:

```bash
curl http://localhost:8081/health
```

Android 에뮬레이터의 백엔드 주소는 `http://10.0.2.2:8081/`입니다. `SERVER_PORT` 환경변수로 포트를 변경할 수 있습니다.

## 공공데이터 환경변수

서비스키는 Android나 Git에 저장하지 않습니다.

백엔드는 실행 디렉터리의 `.env`, 상위 디렉터리의 `.env`, 또는 저장소 루트에서 실행할 때의 `backend/.env`를 선택적으로 읽습니다. 셸 환경변수가 있으면 동일한 이름의 값을 덮어씁니다.

```bash
export DATA_GO_KR_SERVICE_KEY='발급받은키'
export DATA_GO_KR_SERVICE_KEY_ENCODED='true'
```

약–약 분석 요청은 새 약의 공식 품목기준코드와 기존 약의 공식 품목기준코드 목록을 사용합니다.

```bash
curl -X POST http://localhost:8081/api/v1/drug-interaction-checks \
  -H 'Content-Type: application/json' \
  -d '{
    "newMedicationProductCode": "공식_ITEM_SEQ_1",
    "existingMedicationProductCodes": ["공식_ITEM_SEQ_2"]
  }'
```

제품·성분·DUR 조회가 일부라도 실패하면 `NO_KNOWN_ISSUE`로 승격하지 않고 `PARTIAL` 또는 `FAILED`와 `failedSteps`를 반환합니다.

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

`DATA_GO_KR_SERVICE_KEY`와 `DATA_GO_KR_SERVICE_KEY_ENCODED`는 공공 API가 함께 쓰는 하나의 자격증명입니다. 환경변수 이름은 변경하지 않았습니다. 반면 endpoint, timeout, retry, rate limit, bulkhead, circuit breaker 정책과 그 실행 상태는 API별로 분리합니다. 제품 허가정보, DUR, e약은요, 건강기능식품은 각각 독립 executor를 사용합니다. 건강기능식품 원재료 API는 구현하지 않았습니다.

전체 data.go.kr 계정 호출량을 한 번 더 제한하는 전역 rate limiter는 현재 범위에 포함하지 않았습니다. 여러 API를 실제로 연결할 때 계정 quota 정책을 확인한 뒤 별도 계층으로 추가해야 합니다.

주성분 API는 품목코드 요청변수를 제공하지 않으므로 선택 제품의 정확한 `ITEM_NAME`을 `Prduct`로 요청합니다. 응답의 모든 페이지를 수집한 다음 `ITEM_SEQ`가 선택 제품의 품목기준코드와 같은 구조화 레코드만 사용합니다. `MATERIAL_NAME`, `MAIN_ITEM_INGR`, `MAIN_INGR_ENG`를 구분자로 나눠 새로운 공식 성분을 만들지 않습니다.

외부 응답은 `String` 변환 전에 byte 배열로 받고, JSON에 charset이 없으면 UTF-8을 엄격하게 적용합니다. 잘못된 byte 또는 replacement character `�`는 `PUBLIC_API_INVALID_RESPONSE`로 처리합니다. 실제 운영 응답의 `Content-Type` 헤더는 서비스키가 준비된 수동 통합 테스트에서 추가 실측해야 합니다.

DUR 설정 기본값은 확인된 병용금기 operation과 JSON 응답을 사용합니다. `DUR_API_BASE_URL`, `DUR_API_OPERATION_PATH`, `DUR_API_PAGE_SIZE`, `DUR_API_MAX_PAGES`, `DUR_API_MAX_RECORDS` 및 `DUR_API_*` client 정책 환경변수로 덮어쓸 수 있습니다. 서비스키는 DUR 설정에 복제하지 않고 공통 credentials만 사용합니다.

DUR URI는 `ServiceKeyEncoder`가 만든 pre-encoded 값을 `queryParam`에 넣고 `build(true)`로 조립한 뒤 완성된 `URI`를 `RestClient`에 전달합니다. `build(false)`나 form-style 재인코딩을 사용하지 않으므로 `%2F`가 `%252F`로 변하지 않고 raw `+`도 공백으로 바뀌지 않습니다.

e약은요 설정은 `DRUG_OVERVIEW_API_*` 환경변수로 덮어쓸 수 있습니다. 인증 변수명은 provider 명세대로 대문자 `ServiceKey`이며, 공통 `ServiceKeyEncoder`와 `build(true)` 경로를 그대로 사용합니다. 조회는 `itemSeq` 정확 조회를 먼저 수행하고 정상 0건일 때만 공식 제품명 `itemName`과 선택적 `entpName`으로 fallback한 뒤, 모든 페이지에서 원래 품목기준코드를 다시 확인합니다. `efcyQesitm` 등 의료 텍스트를 검색어로 사용하지 않습니다.

건강기능식품 설정은 `HTFS_API_*` 환경변수로 덮어쓸 수 있습니다. 공식 포털 Swagger에는 필터가 노출되지 않았지만 실제 gateway 검증에서 목록의 `Prduct`, `Entrps`, `Sttemnt_no`와 상세의 `STTEMNT_NO`가 동작했습니다. 제공 자료의 `Product`는 실제 gateway에서 필터되지 않았으므로 기본값으로 사용하지 않습니다. 서비스키는 대문자 `ServiceKey`와 기존 1회 인코딩 경로를 사용합니다.

건강기능식품 품목제조신고(원재료)는 기존 `data.go.kr` gateway와 다른 식품안전나라 `C003` 서비스입니다. 이 서비스는 별도 `keyId`를 경로에 전달하고 `startIdx`/`endIdx`로 범위를 지정하며, 제품은 `PRDLST_REPORT_NO`로 필터합니다. [공식 C003 명세](https://www.foodsafetykorea.go.kr/api/openApiInfo.do?menu_grp=MENU_GRP31&menu_no=661&show_cnt=10&start_idx=1&svc_no=C003)는 원재료를 구조화 record 배열이 아닌 제품 record의 단일 `RAWMTRL_NM` 문자열로 제공합니다. [공공데이터포털 설명](https://www.data.go.kr/data/15061756/openapi.do?recommendDataYn=Y)도 세부 하위 원재료와 배합비는 제외한다고 명시합니다.

따라서 현재 확인 자료만으로 `RAWMTRL_NM`을 쉼표 등으로 분해해 공식 `SupplementIngredient`를 생성하지 않습니다. 별도 식품안전나라 키가 준비되지 않았고 실제 정상 성공 및 정상 0건 응답도 재현하지 못했으므로 원재료 production provider, 캐시, evidence bundle 연결은 구현을 중단했습니다. `rawMaterialStatus=NOT_IMPLEMENTED`, `rawMaterials=NotRequested`, `ruleEvidence=NOT_EVALUATED`, `coverage.complete=false`를 유지합니다.

약–건강기능식품 판정은 C003 문자열 대신 사람이 검수한 별도 제품–기능성 원료 매핑과 상호작용 규칙 catalog를 사용합니다. 기본 production catalog는 비어 있으며 의료 규칙을 코드에 하드코딩하거나 seed하지 않습니다. catalog는 `SUPPLEMENT_INTERACTION_RULES_RESOURCE`로 교체할 수 있고 startup 때 schema, source, 검수 상태, canonical 참조, 유효기간, 중복 활성 규칙, manifest count와 SHA-256 checksum을 검증합니다. DRAFT/PENDING/REJECTED/RETIRED 데이터는 production 조회에서 제외합니다.

검수 파일은 원본을 바꾸지 않고 다음 명령으로 검증하고 승인 artifact를 만듭니다. 승인 artifact 생성에는 검수자와 명시적 catalog version이 필수입니다.

```bash
./gradlew validateSupplementRuleCatalog \
  -PcatalogPath=/absolute/path/catalog.json \
  -PreportPath=/absolute/path/validation-report.json

./gradlew buildVerifiedSupplementRuleCatalog \
  -PcatalogPath=/absolute/path/catalog.json \
  -Previewer="검수자 식별값" \
  -PcatalogVersion="2026.08.1" \
  -PoutputPath=/absolute/path/verified-catalog.json
```

기본 설정은 VERIFIED manifest를 요구합니다. 파일 부재, schema/version/checksum 오류 또는 미승인 manifest가 있어도 서버는 시작하지만 catalog를 사용할 수 없고 약–건강기능식품 분석은 `UNKNOWN`과 `RULE_CATALOG_UNAVAILABLE`을 반환합니다. catalog 상태는 인증 없는 public endpoint로 노출하지 않으며 내부 `SupplementRuleCatalogStatusService`에서만 조회합니다.

## API 테스트

건강기능식품 검색은 공식 provider를 먼저 호출하고 정상 미검색인 경우에만 기본 메모리 index를 fallback으로 조회합니다. provider 실패를 빈 후보로 바꾸지 않습니다.

```bash
curl -i \
  -X POST \
  'http://localhost:8081/api/v1/supplement-products/search' \
  -H 'Content-Type: application/json' \
  -d '{"query":"검색할 제품명"}'
```

검색은 공백·괄호·특수문자 제거와 영문 소문자화를 거친 뒤 exact, prefix, contains만 적용합니다. fuzzy/AI matching은 사용하지 않고, 검색 endpoint에서 상세 API를 자동 호출하지 않습니다.

확정한 공식 코드의 약–건강기능식품 분석:

```bash
curl -i \
  -X POST \
  'http://localhost:8081/api/v1/supplement-interaction-checks' \
  -H 'Content-Type: application/json' \
  -d '{"medicationProductCode":"공식 ITEM_SEQ","supplementStatementNo":"공식 STTEMNT_NO"}'
```

`AVOID_COMBINATION`과 `CAUTION`은 VERIFIED 규칙에서만 생성됩니다. 모든 제품·약 성분 코드·검수 원료·Cartesian pair·repository가 완전하지만 일치 규칙이 없을 때만 `NO_VERIFIED_RULE_FOUND`이며, 이는 안전하다는 뜻이 아닙니다. 나머지는 실패 단계를 포함한 `UNKNOWN`입니다.

응답의 `catalogMetadata`, 안정적 enum `failedSteps`, 원문 evidence와 ID를 포함하는 `SupplementInteractionExplanationRequest`가 LLM의 유일한 입력입니다. deterministic analysis가 먼저 끝난 뒤 OpenAI Responses API presentation adapter가 구조화 설명만 생성합니다. 키 미설정이나 provider 장애·부적합 출력은 backend template fallback으로 격리되고 severity·coverage·ID는 바뀌지 않습니다.

Android 연결용 실제 request/response 필드, nullability, enum, Problem Details, emulator 주소와 파일별 변경 계획은 [Android integration contract](docs/android-integration-contract.md)에 고정되어 있습니다. 현재 Android 코드는 아직 이 endpoint에 연결하지 않았습니다.

팀원 OpenAI transport의 환경변수 이름을 유지합니다. 키가 없어도 서버와 deterministic 분석은 정상 동작합니다.

```bash
export OPENAI_API_KEY='서버 전용 키'
export OPENAI_CHAT_MODEL='gpt-4o-mini'
./gradlew externalLlmTest
```

`OPENAI_CONNECT_TIMEOUT`, `OPENAI_READ_TIMEOUT`, `OPENAI_MAX_RETRIES`로 presentation 호출 정책을 제한할 수 있습니다. 실제 prompt, Evidence 원문, request/response 전체와 키는 로그에 남기지 않습니다. 자세한 경계는 [LLM integration](docs/llm-integration.md)에 기록합니다.

```bash
curl -i \
  -X POST \
  'http://localhost:8081/api/v1/drug-products/search' \
  -H 'Content-Type: application/json' \
  -d '{
    "query": "타이레놀 500"
  }'
```

빈 검색어:

```bash
curl -i \
  -X POST \
  'http://localhost:8081/api/v1/drug-products/search' \
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

실제 DUR, e약은요, 건강기능식품 외부 통합 테스트는 기본 `test`에서 제외됩니다. 서비스키 환경변수가 설정되고 각 API 활용신청이 승인된 서버 환경에서만 다음처럼 명시적으로 실행합니다. 테스트는 서비스키, 전체 요청 URL, 전체 응답을 출력하거나 저장하지 않습니다.

```bash
./gradlew externalApiTest
```

## 의료 안전 원칙

- LLM은 공식 후보, 제품 코드, 성분, DUR 결과, 근거를 만들 수 없습니다.
- 공식 제품 후보는 `DrugProductApiClient`의 HTTP 응답으로만 생성합니다.
- API 오류는 정상 빈 결과로 캐시하거나 반환하지 않습니다.
- 성분 조회 실패 후보에는 `ingredientLookupStatus`와 불완전 coverage를 반환합니다.
- 모든 DUR 성분 쌍이 성공해야만 `NO_KNOWN_ISSUE`가 가능합니다.
- e약은요 미제공은 제품·효능 부재를 뜻하지 않으며 `NOT_FOUND` coverage로만 보존합니다.
- 건강기능식품 제품정보를 원재료나 약–보조제 상호작용 근거로 사용하지 않습니다.
- LLM은 판정 엔진이 아니라, 향후 검증된 evidence를 입력받는 설명 생성기로만 허용합니다.
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
- [LLM evidence 경계](docs/llm-evidence-contract.md)
- [LLM integration](docs/llm-integration.md)
- [건강기능식품 검색 인덱스](docs/search-index.md)
- [약–건강기능식품 검수 규칙](docs/supplement-interaction-rules.md)
- [검수 규칙 import schema](docs/supplement-rule-import-schema.md)
- [검수·승인 운영 흐름](docs/supplement-rule-review-workflow.md)
- [Pre-LLM readiness](docs/pre-llm-readiness.md)
- [DB 스키마 초안](docs/database-schema.sql)
