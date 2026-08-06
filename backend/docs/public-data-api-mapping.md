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

## 아직 확인할 사항

- 실제 운영 응답의 `Content-Type` 및 charset 헤더 실측
- 데이터 없음 전용 `resultCode` 존재 여부
- 운영 호출량에 맞는 최대 페이지와 page size
- 상세정보의 문자열 필드는 교차검증이 필요해질 때만 별도 사용 목적을 설계

## DUR

- Base URL: `https://apis.data.go.kr/1471000/DURIrdntInfoService03`
- 공식 데이터셋: https://www.data.go.kr/data/15056780/openapi.do

operation path, 성분코드 요청변수, 관계성분 방향성, 응답 필드는 Swagger 확인 전 구현하지 않습니다. 현재 `SwaggerUnverifiedDurIngredientApiClient`는 항상 실패 상태를 반환합니다.
