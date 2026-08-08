# Supplement Search Index

현재 검색은 실제 gateway에서 검증한 건강기능식품 목록 `Prduct` 필터를 우선 사용한다. 이 문서의 로컬 index는 provider가 정상 `NOT_FOUND`를 반환한 경우에만 사용하는 fallback 경계다.

## 현재 구조

1. `SupplementNameNormalizer`가 NFKC 적용 후 공백·괄호·특수문자를 제거하고 영문을 소문자로 통일한다.
2. `SupplementSearchIndexLoader`가 검증된 `SupplementSearchCandidate` snapshot을 제공한다.
3. `SupplementSearchIndexService`가 exact, prefix, contains 순서로만 점수를 계산한다.
4. `HealthFunctionalFoodService`가 provider `NOT_FOUND`일 때만 index를 조회한다.
5. REST API가 후보의 `STTEMNT_NO`, 공식 제품명, 업체명, match 정보와 source metadata를 반환한다.

점수는 exact 100, prefix 80, contains 60이다. 편집거리, 발음 유사도, 임베딩, LLM 또는 다른 fuzzy matching은 사용하지 않는다. 별칭도 loader가 명시적으로 공급한 값만 검색하며 공식 제품명을 수정하거나 새로 만들지 않는다.

## Loader와 데이터 상태

기본 `InMemorySupplementSearchIndexLoader`는 빈 snapshot을 사용한다. 검증되지 않은 제품 fixture를 애플리케이션에 포함하지 않았으므로 운영 후보를 반환하려면 별도의 공식 데이터 적재 과정이 필요하다.

loader 인터페이스는 다음 구현으로 교체할 수 있다.

- DB snapshot
- 검증된 CSV/JSON snapshot
- 공공데이터 전체 동기화 결과
- Elasticsearch index

동일 `STTEMNT_NO`가 중복 적재되면 최초 한 건만 유지한다. 이름이 충돌하는 중복 데이터를 조용히 합성하지 않으며, 동기화 구현에서는 충돌 검증과 실패 보고를 추가해야 한다.

## REST 계약

`POST /api/v1/supplement-products/search`

요청:

```json
{
  "query": "제품명",
  "manufacturer": "선택 업체명"
}
```

응답 후보 필드:

- `sttemntNo`
- `productName`
- `manufacturer`
- `matchScore`
- `matchType`: `EXACT`, `PREFIX`, `CONTAINS`
- `source`: snapshot 출처와 원본 식별값, 조회 시각

응답에는 `status`, `sourceType`, `complete`가 함께 포함된다. `sourceType`은 `PROVIDER` 또는 `INDEX_FALLBACK`이다.

정상 미검색은 HTTP 200과 `status=NOT_FOUND`, 빈 `candidates`이다. provider 실패는 Problem Details이며 빈 성공으로 변환하지 않는다. blank 또는 정규화 후 빈 검색어는 `VALIDATION_FAILED`다. 검색 endpoint는 상세 provider를 자동 호출하지 않는다.

## 캐시와 안전 경계

캐시 키는 정규화된 query와 선택 업체명이다. 결과가 있는 검색은 positive TTL, 정상 빈 결과는 짧은 negative TTL을 사용한다. provider `FAILED`/`PARTIAL`은 캐시하지 않는다. 인덱스 snapshot이 갱신되는 구현에서는 cache invalidation 또는 index version을 cache key에 포함해야 한다.

이 계층은 제품 후보 탐색만 담당한다. 원재료 API, 약–건강기능식품 상호작용, DUR 연결, LLM, OCR, Android 변경은 포함하지 않는다. 다음 단계는 검증된 공식 전체 snapshot 적재 방식과 index 갱신 정책을 승인한 뒤 진행한다.

## 원재료 조회와의 분리

검색 index의 `STTEMNT_NO`는 현재 제품 provider의 식별값이며, 식품안전나라 `C003`의 제품 필터 후보는 `PRDLST_REPORT_NO`입니다. 두 값의 실제 동일성은 별도 키로 성공 응답을 받아 검증하기 전까지 확정하지 않습니다. index의 제품명이나 alias에서 원재료를 추출하지 않으며, `C003.RAWMTRL_NM` 단일 문자열을 index loader가 분해하거나 보강하지 않습니다. 제품 후보를 찾았다는 사실은 원재료 coverage나 약–건강기능식품 판정을 완성하지 않습니다.
