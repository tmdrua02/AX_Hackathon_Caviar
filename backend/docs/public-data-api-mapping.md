# Public Data API Mapping

## 확인된 사실

데이터셋: 식품의약품안전처 의약품 제품 허가정보

- Base URL: `https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07`
- 제품 검색: `/getDrugPrdtPrmsnInq07`
- 제품 상세: `/getDrugPrdtPrmsnDtlInq06`
- 주성분 상세: `/getDrugPrdtMcpnDtlInq07`
- 제품 검색 요청변수: `serviceKey`, `pageNo`, `numOfRows`, `type`, `item_name`
- JSON/XML 지원
- 공식 데이터셋: https://www.data.go.kr/data/15095677/openapi.do

## 아직 확인되지 않은 매핑

현재 저장소에는 이 서비스 버전의 Swagger raw 응답 또는 실제 API fixture가 없습니다. 다음 값은 코드에서 추측하지 않으며 환경변수 설정 전까지 `PUBLIC_API_MAPPING_UNVERIFIED`로 처리합니다.

- 제품 목록 배열 JSON pointer
- 응답 품목기준코드 필드
- 응답 제품명 필드
- 응답 제조사 필드
- 주성분 조회의 품목코드 요청변수
- 성분 목록 배열 JSON pointer
- 성분코드·한글명·영문명·함량·단위 필드
- 데이터 없음 전용 `resultCode`가 존재하는지 여부

테스트 fixture의 `CODE`, `NAME`, `INGR_NAME` 등은 MockWebServer 전용 내부 테스트 필드이며 공식 필드라고 주장하지 않습니다.

## DUR

- Base URL: `https://apis.data.go.kr/1471000/DURIrdntInfoService03`
- 공식 데이터셋: https://www.data.go.kr/data/15056780/openapi.do

operation path, 성분코드 요청변수, 관계성분 방향성, 응답 필드는 Swagger 확인 전 구현하지 않습니다. 현재 `SwaggerUnverifiedDurIngredientApiClient`는 항상 실패 상태를 반환합니다.
