# 약–건강기능식품 검수 규칙

## 안전 경계

약–약은 공식 의약품 성분과 DUR을 사용하고, 약–건강기능식품은 별도 검수 catalog를 사용한다. 건강기능식품 제품, `STTEMNT_NO`, `MAIN_FNCTN`, `BASE_STANDARD`, `RAWMTRL_NM` 또는 canonical 원료를 DUR에 전달하지 않는다.

제품명과 설명 문자열에서 기능성 원료를 자동 생성하지 않는다. C003은 제품 단위 `RAWMTRL_NM` 문자열만 제공하고 상세 하위 원재료와 배합비가 빠져 있으므로 production 구조화 원료 source로 사용하지 않는다. 향후 개별 원료 record와 제품 식별 연결이 확인된 공식 API가 제공되면 `OFFICIAL_STRUCTURED` mapping loader로 교체한다.

## 검수 계층

1. `VerifiedSourceReference`: 원문, authority, 문서 식별자/URL, 회수 시각과 사람 검수 정보를 보존한다.
2. `SupplementIngredientCanonical`: 사람이 관리한 canonical 이름과 명시적 alias만 보존한다.
3. `SupplementProductIngredientMapping`: 공식 `STTEMNT_NO`와 canonical 원료를 출처 기반으로 연결한다.
4. `SupplementInteractionRule`: 공식 약 성분코드와 canonical 원료 조합의 검수된 AVOID/CAUTION 근거다.

production 조회는 `VERIFIED`이고 현재 유효하며 retire/reject되지 않은 항목만 반환한다. `UNVERIFIED_CANDIDATE`, 자동 split 결과, source 없는 데이터와 중복 활성 규칙은 사용할 수 없다. mechanism은 근거가 없으면 null이고, 사용자 문구와 권고가 원문을 초과하지 않는지는 검수자가 확인한다.

catalog의 source, canonical, mapping, rule ID는 내부 검수 데이터 식별자다. 이를 식약처나 식품안전나라가 발급한 공식 provider record ID로 표시하지 않는다. 공식 제품 식별값은 별도로 보존한 `ITEM_SEQ`와 `STTEMNT_NO`다.

## 판정과 coverage

모든 공식 약 성분과 모든 VERIFIED supplement 원료의 Cartesian product를 평가한다. 하나라도 AVOID가 있으면 AVOID, 그다음 CAUTION 순서다. 확인된 위험은 다른 pair 실패로 삭제하지 않지만 `complete=false`와 실패 pair를 함께 반환한다.

일치 규칙이 없더라도 제품, 모든 공식 약 성분코드, VERIFIED 원료 매핑, 모든 pair 평가와 repository 상태가 완전할 때만 `NO_VERIFIED_RULE_FOUND`다. 이는 안전 판정이 아니다. 그 밖의 결과는 실패 단계를 포함한 `UNKNOWN`이며 항상 의사·약사 상담 안내를 포함한다.
