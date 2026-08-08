# Public Data API Mapping

## 확인된 사실

데이터셋: 식품의약품안전처 의약품 제품 허가정보

- Base URL: `https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07`
- 제품 검색: `/getDrugPrdtPrmsnInq07`
- 제품 상세: `/getDrugPrdtPrmsnDtlInq06`
- 주성분 상세: `/getDrugPrdtMcpnDtlInq07`
- 제품 검색 요청변수: `serviceKey`, `pageNo`, `numOfRows`, `type`, `item_name`
- 제품 검색 목록: `/body/items`
- 제품 검색 필드: `ITEM_SEQ`, `ITEM_NAME`, `ENTP_NAME`, `ITEM_ENG_NAME`, `ENTP_ENG_NAME`
- 제품 상세 정확 조회 요청변수: `item_seq`
- 제품 상세 주요 필드: `ITEM_SEQ`, `ITEM_NAME`, `ENTP_NAME`, `ITEM_PERMIT_DATE`, `MATERIAL_NAME`, `MAIN_ITEM_INGR`, `MAIN_INGR_ENG`, `INGR_NAME`, `ATC_CODE`, `ITEM_ENG_NAME`, `ENTP_ENG_NAME`, `RARE_DRUG_YN`
- 주성분 요청변수: `serviceKey`, `pageNo`, `numOfRows`, `type`, `Entrps_prmisn_no`, `Prduct`, `Entrps`, `Bizrno`
- 주성분 목록: `/body/items`
- 주성분 필드: `ITEM_SEQ`, `PRDUCT`, `MTRAL_SN`, `MTRAL_CODE`, `MTRAL_NM`, `MAIN_INGR_ENG`, `QNT`, `INGD_UNIT_CD`, `TAMT_SEQ`
- JSON/XML 지원
- 공식 데이터셋: https://www.data.go.kr/data/15095677/openapi.do

## 적용 정책

- 제품 검색은 `item_name`만 사용한다.
- 주성분 조회에는 Swagger 원문 철자인 `Prduct`를 사용하며 `item_seq`를 보내지 않는다.
- `Prduct` 결과는 여러 제품을 포함할 수 있으므로 모든 페이지를 읽은 뒤 선택 제품의 `ITEM_SEQ`와 정확히 같은 레코드만 채택한다.
- `MTRAL_CODE`, `MTRAL_NM`, `QNT`, `INGD_UNIT_CD` 구조화 필드를 주성분 기본 데이터로 사용한다.
- `MATERIAL_NAME`, `MAIN_ITEM_INGR`, `INGR_NAME` 문자열을 split해 공식 성분을 생성하지 않는다.
- `MAIN_INGR_ENG`가 `/`로 연결되어 있어도 분해해 별도 공식 성분을 만들지 않는다.
- 제품 상세 operation은 명세를 기록하고 URI를 검증하지만 현재 검색 REST 흐름에서는 추가 호출하지 않는다.
- JSON 응답은 raw byte로 받은 뒤 charset이 없으면 UTF-8로 엄격하게 디코딩한다. malformed/unmappable byte와 `�`는 정상 데이터로 허용하지 않는다.

## 실제 운영 응답 검증 (2026-08-06 KST)

| operation | HTTP status | Content-Type | charset | Content-Encoding | 형식 | 한글 | fixture와의 차이 |
|---|---:|---|---|---|---|---|---|
| `getDrugPrdtPrmsnInq07` | 200 | `application/json` | 없음 | 없음 | JSON | 정상 | 검색 결과가 있으면 기존 `/header`, `/body/items`, `/body/pageNo`, `/body/numOfRows`, `/body/totalCount` 구조 및 `ITEM_SEQ`, `ITEM_NAME`, `ENTP_NAME` 필드와 일치. 성공한 빈 결과(`totalCount=0`)에서는 `body.items`가 빈 배열이 아니라 생략됨 |
| `getDrugPrdtMcpnDtlInq07` | 200 | `application/json` | 없음 | 없음 | JSON | 정상 | 기존 구조 및 `ITEM_SEQ`, `PRDUCT`, `MTRAL_SN`, `MTRAL_CODE`, `MTRAL_NM`, `MAIN_INGR_ENG`, `QNT`, `INGD_UNIT_CD`, `TAMT_SEQ` 필드와 일치 |

- 두 응답 모두 원본 byte의 strict UTF-8 디코딩에 성공했고 replacement character U+FFFD는 없었다.
- `header.resultCode`와 `header.resultMsg`는 `/header`에, 페이지 메타데이터는 `/body`에 존재했다.
- 지정 검색어 `타이레놀 500`은 정상 응답이지만 `totalCount=0`이었고, 제품군 확인용 `타이레놀` 검색은 `totalCount=7`이었다.
- 선택 제품 `202106092`의 정확한 제품명을 `Prduct`로 요청한 주성분 응답은 `totalCount=1`, `numOfRows=20`, 단일 페이지였으며 `ITEM_SEQ` 일치 1건, 제외 0건이었다.
- 제품 상세 operation은 검색·주성분 검증에 필요하지 않아 호출하지 않았다.

## 아직 확인할 사항

- 데이터 없음 전용 `resultCode` 존재 여부
- 운영 호출량에 맞는 최대 페이지와 page size
- 상세정보의 문자열 필드는 교차검증이 필요해질 때만 별도 사용 목적을 설계

## DUR

### 병용금기 구현 및 검증 상태

- 데이터셋: 식품의약품안전처 의약품안전사용서비스(DUR)성분정보
- Base URL: `https://apis.data.go.kr/1471000/DURIrdntInfoService03`
- HTTP method: `GET`
- operation path: `/getUsjntTabooInfoList02`
- 지원 형식: JSON/XML
- 공식 데이터셋: https://www.data.go.kr/data/15056780/openapi.do
- 현재 단계: 실제 provider 조회 client, 응답 매핑, pagination, MockWebServer 테스트, opt-in 외부 통합 테스트 구현 완료

서비스키와 전체 요청 URL/query string은 문서화하지 않는다. `PublicDataDurIngredientApiClient`의 검증된 조회를 공식 약 성분쌍에 한해 양방향 호출하며, 건강기능식품 제품·canonical 원료에는 사용하지 않는다.

### 실제 호출 검증 (최종 재검증: 2026-08-06 KST)

같은 환경변수에서 이미 percent-encoded된 키를 추가 인코딩 없이 전달한 curl과 `ServiceKeyEncoder → queryParam → build(true) → RestClient.uri(URI)` 경로가 모두 성공했다. 반대로 encoded 키를 form-style query encoder에 다시 전달하면 `%2F`가 `%252F`로 변하며 HTTP 403이 재현됐다. 외부 테스트는 키, 전체 URL/query string, 전체 응답을 출력하거나 저장하지 않는다.

| 항목 | 실측 결과 |
|---|---|
| 기준 요청 HTTP status | `200` |
| 응답 형식 | JSON |
| resultCode / resultMsg | `00` / `NORMAL SERVICE.` |
| totalCount | `19` |
| 고유 관계 성분코드 수 | `19` |
| strict UTF-8 | 성공 |
| U+FFFD | 없음 |
| 필수 매핑 | `TYPE_NAME`, `INGR_CODE`, `INGR_KOR_NAME`, `MIXTURE_INGR_CODE`, `MIXTURE_INGR_KOR_NAME` 확인 |
| provider record ID | 확인 가능한 공식 필드 없음; 내부 모델은 `null` 유지 |

현재 구현은 `TYPE_NAME=병용금기`, 요청 `INGR_CODE` 일치, 관계 성분코드 존재를 모든 record에 요구한다. 다른 유형, 기준 코드 불일치, 관계 코드 누락, 잘못된 UTF-8 또는 U+FFFD는 성공 결과로 만들지 않는다.

| 실험 | 관측 | 확정 가능한 결론 |
|---|---|---|
| 코드+이름 | HTTP 200, `resultCode=00`, 19건 | 현재 client의 지원 요청 형태 |
| encoded 키 그대로 전달 | HTTP 200 | wire key의 percent escape 보존 필요 |
| encoded 키 form-style 재인코딩 | HTTP 403 | 이중 인코딩 회귀 조건 확인 |
| 코드만 | 외부 동작 미확인 | URI factory는 이름 생략 가능, service는 안전하게 거부 |
| 이름만 | 미지원 | 기준 공식 코드를 필수로 검증 |
| `typeName` 생략 | 외부 동작 미확인 | client는 항상 `병용금기` 전송 및 응답 유형 검증 |
| 코드·이름 불일치 | 외부 우선순위 미확인 | 응답 기준 코드는 요청 코드와 정확히 일치해야 함 |
| A→B/B→A | 단일 방향 대칭성은 가정하지 않음 | 두 기준 성분을 각각 조회하고 양쪽 complete 필요 |

pagination은 응답 `totalCount`와 `numOfRows`로 총 페이지 수를 계산하고 `maxPages`/`maxRecords`를 적용한다. 모든 페이지가 성공하고 수집한 원본 record 수가 `totalCount`와 같아야 `complete=true`이다. 중간 페이지 실패나 메타데이터 불일치는 `PARTIAL` 또는 `FAILED`이며 `NO_MATCH`로 변환하지 않는다.

정상 빈 결과는 `resultCode=00`, `totalCount=0`, 유효한 페이지 메타데이터와 함께 `items`가 생략되거나 빈 배열인 경우만 인정한다. HTTP 401/403, 429, timeout, 502/503/504, 비정상 resultCode와 오류 envelope는 정상 빈 결과가 아니다.

### 요청 파라미터

| 정확한 이름 | 제공 자료상 타입/값 | 제공 자료상 설명 | 필수 여부 |
|---|---|---|---|
| `serviceKey` | 문자열 | 인증키(URL Encode), 환경변수로만 관리 | 미확인 |
| `pageNo` | 정수, 예시 `1` | 페이지 번호 | 미확인 |
| `numOfRows` | 정수, 예시 `3` | 한 페이지 결과 수 | 미확인 |
| `type` | 문자열, `xml` 또는 `json`; 기본값 `xml` | 응답 형식 | 미확인 |
| `typeName` | 문자열, 확인값 `병용금기` | DUR 유형 | 미확인 |
| `ingrCode` | 문자열, 확인값 `D000762` | 기준 DUR 성분코드 | 미확인 |
| `ingrKorName` | 문자열, 확인값 `이트라코나졸` | 기준 성분 한글명 | 미확인 |

- 파라미터의 대소문자와 철자는 위 형태를 그대로 유지한다.
- 성공 샘플은 `ingrCode`와 `ingrKorName`을 함께 보냈다는 사실만 증명한다. 코드만, 이름만, 또는 서로 불일치하는 값을 보냈을 때의 동작과 우선순위는 확인되지 않았다.
- 관계 성분을 지정하는 요청변수나 성분 A/B를 동시에 넘기는 요청변수는 제공 자료에서 확인되지 않았다.
- 필터 없이 전체 목록을 조회할 수 있는지는 확인되지 않아 C 방식 지원 여부를 확정하지 않는다.

### 조회 방식과 방향성

현재 성공 샘플은 다음 A 방식과 일치한다.

1. `ingrCode`/`ingrKorName`으로 기준 성분 A를 요청한다.
2. 응답의 `INGR_CODE`가 기준 성분을 나타낸다.
3. 각 중첩 `item`의 `MIXTURE_INGR_CODE`가 관계 성분 B를 나타낸다.
4. 관계 성분은 결합 문자열이 아니라 공식 코드가 포함된 개별 레코드로 반환된다.

샘플 수준에서는 A 방식이 확인되지만 공식 문서에 관계 방향성이 명시됐다는 근거는 없다. A 조회에서 B가 반환되더라도 B 조회에서 A가 반드시 반환된다고 가정하지 않는다. 이후 성분 쌍 조회를 구현할 때는 역방향 실험과 공식 명세가 확보되기 전까지 A→B와 B→A를 모두 완전 조회하는 방식을 안전한 후보로 둔다. 어느 한 방향에서 관계가 확인되면 그 근거를 보존할 수 있지만, 병용금기 없음은 양방향 조회와 모든 페이지가 모두 성공한 경우에만 후보가 될 수 있다.

### 확인된 JSON 구조

| 값 | JSON Pointer |
|---|---|
| result code | `/header/resultCode` |
| result message | `/header/resultMsg` |
| body | `/body` |
| page number | `/body/pageNo` |
| page size | `/body/numOfRows` |
| total count | `/body/totalCount` |
| items | `/body/items` |
| 개별 provider record | `/body/items/*/item` |

성공 샘플은 `resultCode="00"`, `resultMsg="NORMAL SERVICE."`였다. 의약품 허가정보 응답과 달리 `/body/items` 배열의 각 원소 안에 `item` 객체가 한 단계 더 중첩된다.

### provider 필드 매핑

아래 필드명과 중첩 구조는 실제 성공 응답과 외부 통합 테스트에서 확인했다. 공식 설명이 확인되지 않은 부가 필드는 `rawFields`에도 보존하며 의미를 새로 만들지 않는다.

| provider field | Swagger 설명 상태 | sample value | 내부 필드 후보 | nullable | 공식 식별값 | 용도 |
|---|---|---|---|---|---|---|
| `TYPE_NAME` | DUR 유형 | `병용금기` | `typeName` | client 필수 | 아니오 | 요청 유형과 정확 일치 검증 |
| `MIX_TYPE` | 원문 미확인 | 한글 손상 | `ingredientMixType` | 미확인 | 아니오 | 표시/원본 보존 후보 |
| `INGR_CODE` | 기준 DUR 성분코드 | `D000762` | `ingredientCode` | 샘플은 non-null | 예, 성분 식별 | 기준 성분 코드 비교 |
| `INGR_ENG_NAME` | 기준 성분 영문명 | `Itraconazole` | `ingredientEnglishName` | 샘플은 non-null | 아니오 | 표시/교차검증 |
| `INGR_KOR_NAME` | 기준 성분 한글명 | `이트라코나졸` | `ingredientKoreanName` | client 필수 | 아니오 | 한글 원문 보존 |
| `MIX` | 원문 미확인 | 빈 문자열 | `rawFields[MIX]` | 빈 문자열 확인 | 아니오 | 원본 보존만 |
| `ORI` | 원문 미확인 | 한글 손상 원료 문자열 | `rawFields[ORI]` | 미확인 | 아니오 | 원본 보존; 분해 금지 |
| `CLASS` | 원문 미확인 | 한글 손상 | `rawFields[CLASS]` | 미확인 | 아니오 | 표시 후보; 정상 byte 재검증 필요 |
| `MIXTURE_MIX_TYPE` | 원문 미확인 | 한글 손상 | `relatedIngredientMixType` | 미확인 | 아니오 | 표시/원본 보존 후보 |
| `MIXTURE_INGR_CODE` | 관계 DUR 성분코드 | `D000027` 등 | `relatedIngredientCode` | 샘플은 non-null | 예, 성분 식별 | 관계 성분 코드 비교의 우선값 |
| `MIXTURE_INGR_ENG_NAME` | 관계 성분 영문명 | `Simvastatin` 등 | `relatedIngredientEnglishName` | 샘플은 non-null | 아니오 | 표시/교차검증 |
| `MIXTURE_INGR_KOR_NAME` | 관계 성분 한글명 | 정상 한글 확인 | `relatedIngredientKoreanName` | client 필수 | 아니오 | 한글 원문 보존 |
| `MIXTURE_MIX` | 원문 미확인 | 빈 문자열 | `rawFields[MIXTURE_MIX]` | 빈 문자열 확인 | 아니오 | 원본 보존만 |
| `MIXTURE_ORI` | 원문 미확인 | 한글 손상 원료 문자열 | `rawFields[MIXTURE_ORI]` | 미확인 | 아니오 | 원본 보존; 분해 금지 |
| `MIXTURE_CLASS` | 원문 미확인 | 한글 손상 | `rawFields[MIXTURE_CLASS]` | 미확인 | 아니오 | 표시 후보; 정상 byte 재검증 필요 |
| `NOTIFICATION_DATE` | 원문 미확인; 고시일 후보 | `20090303` | `notificationDate` | 샘플은 non-null | 단독 ID 아님 | 날짜 파싱 전 `yyyyMMdd` 명세 확인 |
| `PROHBT_CONTENT` | 병용금기 내용 | 정상 한글 확인 | `prohibitionContent` | nullable | 아니오 | 원문 보존 |
| `REMARK` | 비고로 보임 | `null` | `remark` | 예 | 아니오 | 선택 표시/원본 보존 |
| `DEL_YN` | 원문 미확인; 삭제/상태 후보 | 한글 손상 | `providerStatus` | 미확인 | 아니오 | 활성 여부 판단 전 공식 값 필요 |

`ORI`, `MIXTURE_ORI` 및 다른 문자열을 분해하여 공식 성분이나 관계를 생성하지 않는다. 한국어가 손상됐을 때 영문명을 한국어 필드에 대체하지 않는다.

### pagination과 완전성

외부 통합 테스트는 설정 page size 100으로 `totalCount=19`를 한 페이지에서 완전 수집했다. page size를 줄인 MockWebServer 테스트로 다중 페이지 병합과 중간 실패를 검증한다.

- 예상 페이지 수는 `ceil(totalCount / numOfRows)`로 계산하되 각 페이지가 요청 page와 일치하는지 검증한다.
- 모든 페이지와 모든 중첩 `item`이 성공적으로 수집된 경우에만 `complete=true` 후보가 된다.
- 첫 페이지만 성공하거나 중간 페이지가 실패하면 `NO_MATCH` 또는 완전 조회로 처리하지 않는다.
- 페이지별 `totalCount`/`numOfRows` 불일치, 구조 오류, 최종 수신 건수 불일치는 provider failure 또는 incomplete 상태다.
- 기본 안전 한도는 `maxPages=100`, `maxRecords=10000`이며 환경변수로 조정한다. 한도를 넘으면 완전 성공으로 반환하지 않는다.
- provider가 보장하는 공식 레코드 ID가 없으므로 `(INGR_CODE, MIXTURE_INGR_CODE, NOTIFICATION_DATE, TYPE_NAME, PROHBT_CONTENT)`로만 내부 중복을 제거한다.
- 위 조합은 공식 record ID가 아니며 `providerRecordId`는 계속 `null`이다. 서로 다른 고시일 또는 금기 문구의 레코드는 유지한다.

### 정상 빈 결과와 provider failure

client 계층의 정상 0건 조건은 `resultCode=00`, `totalCount=0`, 유효한 `pageNo`/`numOfRows`이고 `/body/items`가 생략 또는 빈 배열인 경우다. 두 형태를 MockWebServer fixture로 검증했다. 실제 provider의 0건 표본은 후속 외부 검증 항목으로 남는다.

향후 `NO_MATCH` 후보 조건은 HTTP 성공, 정상 `resultCode`, 유효한 구조, 필요한 모든 방향과 모든 페이지의 성공, 명세로 확인된 0건 표현, 파싱/인코딩 실패 없음이다. 다음은 정상 빈 결과가 아니라 provider failure 또는 incomplete로 분리한다.

- 인증/권한/호출량 제한/timeout/provider 5xx 등 HTTP 실패
- 비정상 또는 누락된 `resultCode`
- malformed JSON/XML, 필수 구조 또는 페이지 메타데이터 오류
- 중간 페이지 실패나 최종 수신 건수 불일치
- 원본 byte 디코딩 실패 또는 U+FFFD 포함
- 요청 기준 성분과 응답 `INGR_CODE` 불일치

HTTP 401/403은 `PUBLIC_API_AUTH_FAILED`, 429는 `PUBLIC_API_QUOTA_EXCEEDED`, timeout은 `PUBLIC_API_TIMEOUT`, 502/503/504는 `PUBLIC_API_UNAVAILABLE`로 분리한다. non-`00` resultCode와 구조·인코딩 오류는 provider 실패이며 `NO_MATCH`가 아니다.

### 원본 record ID와 내부 모델

실제 성공 응답의 확인된 필드에는 `DUR_SEQ` 또는 다른 공식 레코드 ID가 없다. `providerRecordId=null`을 유지하며 내부 UUID나 hash를 식약처 공식 DUR 번호로 표시하지 않는다.

구현 모델:

- `DurLookupRequest`: `ingredientCode`, `ingredientKoreanName`, `lookupDirection`
- `DurProviderRecord`: `providerRecordId`, 기준·관계 성분 코드/이름, `typeName`, `prohibitionContent`, `notificationDate`, `remark`, `providerStatus`, `rawFields`
- `DurInteractionEvidence`: 출처/기관, nullable 원본 ID, 좌·우 성분코드, 조회 방향, 원문, 고시일, 조회시각
- `DurLookupResult`: `status`, `records`, `totalCount`, `completedPages`, `failedPages`, `complete`

provider에 없는 값은 채우지 않는다. 기존 의약품 허가정보 DTO도 재사용하지 않는다.

### 인코딩 및 남은 확인 사항

성공 응답을 raw `ByteArray`로 수신해 strict UTF-8로 해석했으며 U+FFFD가 없고 주요 한국어 필드가 정상임을 확인했다. CP949/EUC-KR fallback은 추가하지 않는다. 남은 확인 사항은 다음과 같다.

1. 실제 정상 0건 provider 표본과 공식 오류표
2. `ingrCode` 단독, `ingrKorName` 단독, 두 값 불일치 실험
3. 관계 성분 역방향 조회와 공식 방향성 설명
4. Swagger 요청변수 필수 여부 및 출력결과 필드 설명 원문
5. 공식 record ID 제공 여부의 문서 확인
6. `DEL_YN`, `MIX`, `ORI`, `CLASS` 및 대응 `MIXTURE_*` 필드의 공식 의미와 허용값

pair 경계는 A와 B 각각을 기준 성분으로 조회한다. 어느 방향이든 ACTIVE 관계가 있으면 원문 위험 근거를 보존하고, 양방향이 모두 complete인 경우에만 관계 없음 후보를 반환한다. 일부 실패가 있으면 확인된 위험은 유지할 수 있지만 coverage는 incomplete다.

## 의약품개요정보(e약은요)

### 확인된 명세와 실제 구조

- Base URL: `https://apis.data.go.kr/1471000/DrbEasyDrugInfoService`
- operation: `GET /getDrbEasyDrugList`
- 인증 변수: 대문자 `ServiceKey`
- 검색 변수: `itemSeq`, `itemName`, `entpName`; 의료 설명 필드는 검색에 사용하지 않음
- pagination: `pageNo`, `numOfRows`; 응답 형식: `type=json`
- 공식 데이터셋: https://www.data.go.kr/data/15075057/openapi.do

실제 호출에서 HTTP 200, `Content-Type: application/json`(charset 없음), strict UTF-8 성공, U+FFFD 없음이 확인됐다. 응답은 `/header/resultCode`, `/header/resultMsg`, `/body/pageNo`, `/body/numOfRows`, `/body/totalCount`이고 `/body/items`는 wrapper가 없는 record 직접 배열이다. 정상 0건은 `resultCode=00`, 유효한 페이지 메타데이터와 함께 `items`가 생략됐다. 구현은 빈 배열도 정상 0건으로 수용한다.

| provider field | 내부 매핑 | 정책 |
|---|---|---|
| `itemSeq` | `productCode` | 입력 품목기준코드와 정확 일치 필수 |
| `itemName` | `productName` | 공식 제품명 교차검증 |
| `entpName` | `manufacturer` | 복수 후보 보조 검증 |
| `efcyQesitm` | `efficacy` | 공식 효능 원문만 사용 |
| `useMethodQesitm` | `usageMethod` | 원문/표시용 분리 |
| `atpnWarnQesitm` | `warning` | 원문/표시용 분리 |
| `atpnQesitm` | `precautions` | 원문/표시용 분리 |
| `intrcQesitm` | `interactions` | 원문/표시용 분리; 최종 판정 근거로 승격하지 않음 |
| `seQesitm` | `sideEffects` | 원문/표시용 분리 |
| `depositMethodQesitm` | `storageMethod` | 원문/표시용 분리 |
| `itemImage` | `imageUrl` | 공식 이미지 URL |
| `openDe` | `openDate` | provider 문자열 보존 |
| `updateDe` | `updateDate` | provider 문자열 보존 |

조회는 `itemSeq` 정확 조회를 먼저 하고 정상 0건일 때만 `itemName`과 선택적 `entpName`으로 fallback한다. 모든 페이지를 수집한 후에도 원래 `itemSeq`와 일치하는 record만 채택한다. 첫 결과, 유사 제품, 다른 품목기준코드를 자동 채택하지 않는다. 일부 페이지 실패는 `PARTIAL`, provider 실패는 `FAILED`, 완전한 정상 0건 또는 정확 제품 부재만 `NOT_FOUND`이다.

e약은요는 공급실적이 있는 일반의약품 중심이므로 `NOT_FOUND`를 제품 부재나 효능 없음으로 해석하지 않는다. overview는 기존 medication과 ingredients를 대체하지 않는 보조 snapshot이다. HTML이 있으면 raw를 그대로 보존하고 표시용 값은 태그 제거와 entity 해제만 수행하며 요약·의미 변경을 하지 않는다.

서비스키는 공통 `ServiceKeyEncoder`로 정확히 한 번 인코딩하고 `queryParam`과 `build(true)`로 조립한 완성 `URI`를 전용 RestClient에 전달한다. e약은요는 독립 `drugOverviewCallExecutor`를 사용하며 오류 결과를 캐시하지 않는다.

### 실제 외부 테스트 (2026-08-06 KST)

- 정확 품목기준코드 조회: `resultCode=00`, `RESOLVED`
- 존재하지 않는 품목기준코드 및 제품명 fallback: 정상 `NOT_FOUND`
- strict UTF-8 및 U+FFFD 부재 확인
- 서비스키, 전체 요청 URL, query string, 전체 응답은 출력하거나 저장하지 않음

## 건강기능식품정보

### operation과 요청변수 검증

- Base URL: `https://apis.data.go.kr/1471000/HtfsInfoService03`
- 목록: `GET /getHtfsList01`
- 상세: `GET /getHtfsItem01`
- 공식 데이터셋: https://www.data.go.kr/data/15056760/openapi.do

2026-08-06에 내려받은 공식 포털 Swagger 원문에는 두 operation 모두 `pageNo`, `numOfRows`, 대문자 `ServiceKey`, `type`만 노출됐다. 다만 실제 gateway 호출에서는 다음 필터 동작을 확인했다.

| operation | 파라미터 | 실제 결과 |
|---|---|---|
| 목록 | `Prduct` | 제품명 필터 동작, 실측 표본 `totalCount=9` |
| 목록 | `Entrps` | 업체명 필터 동작, 실측 표본 `totalCount=538` |
| 목록 | `Sttemnt_no` | 품목제조관리번호 정확 필터, 실측 `totalCount=1` |
| 상세 | `STTEMNT_NO` | 품목제조관리번호 정확 필터, 실측 `totalCount=1` |
| 목록 | `Product` | 필터되지 않고 전체 `totalCount=45427` 반환 |

따라서 구현 기본값은 실제 gateway에서 동작한 철자와 대소문자를 사용한다. `Product`를 `Prduct`의 alias로 가정하지 않으며, 환경변수로 parameter 이름을 바꿀 수 있다.

| operation | 확인된 출력 필드 |
|---|---|
| 목록 | `ENTRPS`, `PRDUCT`, `STTEMNT_NO`, `REGIST_DT` |
| 상세 | 목록 필드 + `DISTB_PD`, `SUNGSANG`, `SRV_USE`, `PRSRV_PD`, `INTAKE_HINT1`, `MAIN_FNCTN`, `BASE_STANDARD` |

### 응답 구조와 매핑

JSON은 다음 실제 구조만 매핑한다.

| 값 | JSON Pointer |
|---|---|
| result code | `/header/resultCode` |
| result message | `/header/resultMsg` |
| page number | `/body/pageNo` |
| page size | `/body/numOfRows` |
| total count | `/body/totalCount` |
| wrapper array | `/body/items` |
| record | `/body/items/*/item` |

상세 snapshot 매핑:

| provider field | 내부 필드 |
|---|---|
| `STTEMNT_NO` | `statementNo` |
| `PRDUCT` | `productName` |
| `ENTRPS` | `manufacturer` |
| `REGIST_DT` | `registerDate` |
| `DISTB_PD` | `distributionPeriod` |
| `SUNGSANG` | `appearance` |
| `SRV_USE` | `usage` |
| `PRSRV_PD` | `storage` |
| `INTAKE_HINT1` | `intakeHint` |
| `MAIN_FNCTN` | `mainFunction` |
| `BASE_STANDARD` | `baseStandard` |

확인된 record 전체는 `rawProviderRecord`로 보존한다. Swagger에 없는 의료 의미나 원재료 필드는 만들지 않는다.

### 실제 호출 검증

| 항목 | 결과 |
|---|---|
| 목록·상세 HTTP status | `200` |
| resultCode / resultMsg | `00` / `NORMAL SERVICE.` |
| Content-Type | `application/json` |
| charset | 없음; JSON strict UTF-8 적용 |
| Content-Encoding | 없음 |
| strict UTF-8 | 성공 |
| U+FFFD | 없음 |
| 한글 | `PRDUCT`, `ENTRPS`, `MAIN_FNCTN`, `INTAKE_HINT1`, `BASE_STANDARD` 정상 |
| 정상 빈 목록 | `resultCode=00`, `totalCount=0`, items 생략 |
| 정상 빈 상세 | `resultCode=00`, `totalCount=0`, items 생략 |

opt-in Kotlin 외부 통합 테스트에서 제품명 검색은 `RESOLVED`, `totalCount=9`, 상세 조회는 `RESOLVED`였고 필수 한글 필드 5개를 검증했다. 서비스키, 전체 URL/query string 및 전체 응답은 출력하거나 저장하지 않는다.

### 상태, pagination과 캐시

- 모든 페이지 성공과 수신 record 수 일치 시에만 `RESOLVED` 또는 `NOT_FOUND`, `complete=true`
- 정상 0건만 `NOT_FOUND`
- 중간 페이지 실패 또는 한도 초과는 `PARTIAL`, `complete=false`
- 첫 페이지 실패, HTTP/provider/구조/인코딩 오류는 `FAILED`
- HTTP 401/403, 429, timeout, 502/503/504를 각각 기존 공통 오류코드로 분리
- provider `RESOLVED`와 `NOT_FOUND`만 검색·상세 캐시에 저장
- `FAILED`와 `PARTIAL`은 캐시하지 않음
- 검색은 provider 우선이며 provider `NOT_FOUND`일 때만 `SupplementSearchIndex` fallback 사용
- provider 실패를 index 결과나 정상 빈 결과로 숨기지 않음

이 API는 제품 기본정보 provider이며 원재료 API가 아니다. 제품 snapshot은 약–건강기능식품 분석의 제품 식별 근거로만 사용하고, 원료는 별도 VERIFIED mapping에서만 가져온다. 제품 설명을 DUR이나 LLM에 원료로 전달하지 않으며 C003 자동 분해도 하지 않는다.

## 건강기능식품 품목제조신고(원재료) C003 검증

- 데이터셋: [식품의약품안전처 건강기능식품 품목제조신고(원재료)](https://www.data.go.kr/data/15061756/openapi.do?recommendDataYn=Y)
- 공식 명세: [식품안전나라 C003 데이터활용서비스](https://www.foodsafetykorea.go.kr/api/openApiInfo.do?menu_grp=MENU_GRP31&menu_no=661&show_cnt=10&start_idx=1&svc_no=C003)
- host: `openapi.foodsafetykorea.go.kr`
- HTTP method: `GET`
- operation template: `/api/{keyId}/C003/{dataType}/{startIdx}/{endIdx}`

### 확인된 요청 경계

| 구분 | 확인된 값 |
|---|---|
| 필수 | `keyId`, `serviceId=C003`, `dataType`, `startIdx`, `endIdx` |
| 선택 필터 | `CHNG_DT`, `PRDLST_REPORT_NO`, `PRMS_DT`, `PRDLST_NM`, `BSSH_NM`, `LCNS_NO` |
| 제품 식별 필터 | `PRDLST_REPORT_NO`(품목제조번호) |
| pagination | 1-based `startIdx`/`endIdx`, 1회 최대 1000건 |
| 인증 | 식품안전나라 별도 `keyId`; data.go.kr `ServiceKey` query 방식이 아님 |

공식 출력 필드는 `LCNS_NO`, `BSSH_NM`, `PRDLST_REPORT_NO`, `PRDLST_NM`, `PRMS_DT`, `POG_DAYCNT`, `DISPOS`, `NTK_MTHD`, `PRIMARY_FNCLTY`, `IFTKN_ATNT_MATR_CN`, `CSTDY_MTHD`, `SHAP`, `STDR_STND`, `RAWMTRL_NM`, `CRET_DTM`, `LAST_UPDT_DTM`, `PRDT_SHAP_CD_NM`입니다. 실제 오류 응답에서는 `C003.RESULT.CODE`/`MSG`와 `C003.total_count`를 확인했습니다. `INFO-000`은 정상, 공식 명세의 `INFO-200`은 정상 미검색이며 인증·권한·quota·server 오류 코드와 구분됩니다. 정상 JSON의 record 목록 위치와 wrapper 형태는 이번 검증에서 확인하지 못했습니다.

### 구현 중단 결정

`RAWMTRL_NM`은 제품 record 안의 단일 문자열이며 item wrapper나 구조화된 원재료 목록이 아닙니다. 공식 필드에는 원재료 코드, 기능성/주/부원료 구분, 함량, 단위, 배합비, 원재료 순번 또는 원재료 record ID가 없습니다. 데이터셋 설명상 세부 하위 원재료와 배합비도 제외됩니다. 따라서 이 문자열을 구분자로 나누면 복합원재료와 괄호 내부 조성을 오인할 수 있고, 공식 record가 아닌 추측 데이터를 만들게 됩니다.

2026-08-06 KST 실제 샘플 호출은 HTTP 200, `Content-Type: application/json;charset=utf-8`, strict UTF-8 성공, U+FFFD 없음이었지만 본문은 `ERROR-503`, `total_count=0`, row 생략이었습니다. 이는 provider 오류이며 정상 0건으로 취급할 수 없습니다. 별도 식품안전나라 키가 설정되지 않아 현재 제품의 `PRDLST_REPORT_NO` 정확 조회, `INFO-000` 성공, `INFO-200` 정상 0건은 실측하지 못했습니다.

필수 중단 조건인 원재료 목록 위치와 실제 정상 성공/빈 응답을 충족하지 못했으므로 Kotlin DTO, client, mapper, cache, executor 및 bundle 연결을 만들지 않았습니다. 기존 `STTEMNT_NO`와 `PRDLST_REPORT_NO`의 실제 동일성도 성공 응답으로 검증하지 않았습니다. 진행하려면 구조화된 제품-원재료 record API의 공식 명세와 실제 성공/빈 fixture, 그리고 승인된 식품안전나라 전용 키가 필요합니다.
