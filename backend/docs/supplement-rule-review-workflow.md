# Supplement Rule Review Workflow

## 역할과 입력

- 작성자는 사용자 제공 자료 또는 승인된 내부 검수 자료만 JSON catalog로 옮긴다.
- 검수자는 source 원문, 공식 식별값, canonical 원료, 제품 매핑, severity와 문구의 근거 범위를 독립적으로 확인한다.
- import 도구는 의료 내용을 생성·요약·보완하지 않는다. 인터넷 자동 수집이나 LLM 입력도 사용하지 않는다.
- 저장소의 production catalog는 빈 배열이며 실제 의료 규칙 seed가 없다.

Catalog lifecycle은 `DRAFT`, `VALIDATION_FAILED`, `READY_FOR_REVIEW`, `VERIFIED`, `REJECTED`, `RETIRED`를 구분한다. record lifecycle은 기존 `EvidenceVerificationStatus`를 사용한다. VERIFIED source에는 `reviewedBy`와 `reviewedAt`이 반드시 있어야 하고 VERIFIED mapping/rule은 VERIFIED source와 canonical만 참조한다.

## 검증과 승인

1. 작성 catalog를 저장소 밖 작업 경로에 둔다.
2. `validateSupplementRuleCatalog`로 schema/semantic report를 생성한다.
3. errors가 0인지 검수자가 확인한다. warning도 승인 전에 검토한다.
4. 검수자 식별값과 catalog version을 명시해 `buildVerifiedSupplementRuleCatalog`를 실행한다.
5. 생성 artifact를 다시 production 정책으로 검증한다.
6. 승인 artifact 경로만 `SUPPLEMENT_INTERACTION_RULES_RESOURCE`에 설정한다.

```bash
./gradlew validateSupplementRuleCatalog \
  -PcatalogPath=/review/catalog.json \
  -PreportPath=/review/catalog-report.json

./gradlew buildVerifiedSupplementRuleCatalog \
  -PcatalogPath=/review/catalog.json \
  -Previewer="reviewer-id" \
  -PcatalogVersion="2026.08.1" \
  -PgeneratedBy="author-id" \
  -PoutputPath=/approved/verified-catalog.json \
  -PreportPath=/approved/verified-catalog-report.json
```

승인 도구는 원본을 수정하지 않고 별도 artifact를 원자적으로 기록한다. 오류가 있거나 reviewer/catalogVersion이 없거나 input과 output 경로가 같으면 artifact를 만들지 않는다.

## Manifest와 운영

manifest에는 catalog/schema version, 생성자·생성 시각, 검수자·검수 시각, source input SHA-256, section별 record count, 상태와 content SHA-256이 포함된다. content checksum은 manifest를 제외한 네 data section의 canonical serialization을 기준으로 하므로 startup에서 재계산할 수 있다.

파일/manifest 부재, `status != VERIFIED`, schema version 오류, count/checksum 오류 또는 semantic validation 실패가 있으면 서버는 시작하지만 catalog는 unavailable이다. 분석 결과는 `UNKNOWN`, `RULE_CATALOG_UNAVAILABLE`, incomplete coverage를 보존한다. 운영 로그에는 resource 경로와 오류 코드만 남고 source 원문은 기록하지 않는다.

인증된 관리 plane이 아직 없으므로 catalog 상태 HTTP endpoint는 제공하지 않는다. `SupplementRuleCatalogStatusService`가 원문 없이 version, checksum, loadedAt, count와 validation error code만 내부에서 조회한다.
