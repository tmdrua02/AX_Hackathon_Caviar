# Pre-LLM Readiness

## Implemented

- 공식 의약품 `ITEM_SEQ` 식별과 전체 구조화 주성분 조회
- e약은요 overview 조회와 성분 coverage에서 분리된 optional overview coverage
- 공식 약 성분 전체 Cartesian product와 DUR A→B/B→A 조회
- 건강기능식품 검색, `STTEMNT_NO` 확정과 공식 상세 snapshot
- VERIFIED product-to-canonical ingredient mapping repository
- manifest/checksum이 검증된 VERIFIED rule catalog governance
- 공식 약 성분 코드 × VERIFIED canonical ID deterministic rule engine
- AVOID 우선, CAUTION 차순, 완전 coverage에서만 `NO_VERIFIED_RULE_FOUND`, 그 외 `UNKNOWN`
- positive risk + partial coverage에서 위험 severity 보존
- stable `SupplementInteractionFailureCode`
- semantic checkpoint와 pair 평가를 함께 반영하는 coverage
- 원문과 공식/검수 ID를 보존하는 Evidence Bundle
- catalog version/schema/checksum/loadedAt audit metadata
- `POST /api/v1/supplement-interaction-checks` 실제 service orchestration
- JSON 직렬화 가능한 `SupplementInteractionExplanationRequest`

## LLM integration completed after this checkpoint

- 팀원 OpenAI Responses API transport와 structured output 패턴을 Kotlin/Jackson 3 adapter로 통합
- `SupplementInteractionPresentationService` orchestration
- deterministic fallback과 UNKNOWN/no-rule safe-claim guard
- additive REST explanation
- no-key startup, MockWebServer와 opt-in `externalLlmTest`

## Not implemented intentionally

- 자유형 의료 채팅
- 진료 음성 전사·요약
- LLM 기반 severity/rule/제품/원료 생성
- 팀원 Android 및 별도 Java server 전체 병합
- Android 변경
- OCR 변경
- C003 `RAWMTRL_NM` 자동 분해
- 실제 production 의료 규칙 seed

약–건강기능식품 endpoint는 동기식입니다. 기존 Android에 선언된 약–약 비동기 `POST/GET /interaction-checks` 및 영속 상태 전이는 아직 public API로 구현하지 않았지만, 내부 `DrugInteractionAnalysisService`는 공식 두 제품 → 전체 성분 → Cartesian DUR 비교를 수행합니다. 큐나 신규 DB는 이번 단계에 추가하지 않았습니다.

## External dependency / data gaps

production `supplement-interaction-rules.json`은 실제 의료 record가 없는 빈 catalog입니다. 따라서 production에서는 VERIFIED 제품–원료 mapping이 없고 약–건강기능식품 분석은 `UNKNOWN`과 `SUPPLEMENT_INGREDIENT_MAPPING_MISSING`을 반환해야 합니다. catalog manifest가 없거나 미승인/불일치하면 `RULE_CATALOG_UNAVAILABLE` 또는 `RULE_CATALOG_INVALID`도 함께 보존합니다.

C003은 별도 식품안전나라 키가 필요하고 `RAWMTRL_NM` 단일 문자열만 제공하며, `STTEMNT_NO`와 `PRDLST_REPORT_NO` 동일성이 검증되지 않았습니다. 이 문자열을 구조화 공식 원료로 분해하지 않습니다. 실제 production readiness에는 전문가가 검수한 source, canonical ingredient, `STTEMNT_NO` mapping, interaction rule과 VERIFIED manifest가 필요합니다.

## LLM integration contract

팀원은 다음 코드만 연결점으로 사용합니다.

- deterministic service: `com.haneul.medassist.service.SupplementInteractionAnalysisService`
- immutable 변환: `SupplementInteractionAnalysisResult.toExplanationRequest()`
- 입력 DTO: `com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest`
- REST orchestration: `com.haneul.medassist.controller.SupplementInteractionController`

DTO의 `immutableDecision`, product/ingredient/canonical/rule/source ID, `catalogMetadata`, `coverage`, `failedSteps`, `evidence.originalText`, `disclaimer`는 LLM이 변경할 수 없습니다. LLM 출력이 이 값과 충돌하면 backend 값을 사용합니다. LLM은 설명문만 생성하는 presentation layer입니다.

## Readiness distinction

- `CODE_READY_FOR_LLM`: provider orchestration, deterministic engine, Evidence/coverage/failure taxonomy, serialization, REST E2E, 전체 테스트와 build가 성공할 때 YES
- `PRODUCTION_DATA_READY`: VERIFIED source/canonical/mapping/rule 및 전문가 review metadata가 있는 VERIFIED manifest catalog가 실제 적재됐을 때만 YES

코드 readiness와 의료 데이터 readiness는 서로 독립적입니다. 빈 production catalog에서는 `PRODUCTION_DATA_READY = NO`입니다.
