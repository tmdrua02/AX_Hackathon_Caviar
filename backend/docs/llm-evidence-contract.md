# LLM Evidence Contract

현재 LLM 연동 코드는 구현하지 않는다. 향후에도 LLM은 판정 엔진이 아니라 근거 기반 설명 생성기다.

## 허용 입력

`EvidencePresentationRequest` 같은 별도 DTO에는 backend가 이미 확정한 다음 값만 포함할 수 있다.

- 판정 status와 coverage
- 공식 제품명·성분·효능 원문·주의사항
- 완전성 검증을 통과한 DUR 근거
- 별도 검수된 supplement rule 근거
- 실패 단계와 상담 안내

## 금지 동작

- 최종 status 또는 coverage 변경
- 제품, 성분, 건강기능식품 원재료 생성
- DUR 관계나 supplement rule 생성
- `UNKNOWN`, `FAILED`, `PARTIAL`을 안전 또는 병용 가능으로 변경
- 공식 근거가 없는 효능·진단·상호작용 설명 생성

현재 `MedicationEvidenceBundle`은 medication, ingredients, 선택적 overview와 DUR evidence를 분리한다. `SupplementEvidenceBundle`은 제품 기본정보와 원재료·규칙 근거를 구분하며, 원재료 API가 구현되기 전까지 `rawMaterialStatus=NOT_IMPLEMENTED`, `rawMaterials=NotRequested`, `ruleEvidence=NOT_EVALUATED`, `coverage.complete=false`이다.

식품안전나라 `C003`의 `RAWMTRL_NM`은 구조화 원재료 record가 아닌 제품 단위 원문 문자열입니다. backend도 LLM도 이 문자열을 분해해 원재료 코드, 유형, 함량, 단위 또는 배합비를 생성할 수 없습니다. 향후 공식 구조화 원재료 근거가 확보되더라도 검수된 supplement rule이 없으면 LLM 입력의 전체 coverage는 `false`이고, LLM은 그 상태를 병용 가능으로 바꿀 수 없습니다.

## 약–건강기능식품 설명 입력

`SupplementInteractionExplanationRequest`는 backend가 확정한 `immutableDecision`, 공식 제품명·약 성분, VERIFIED canonical 원료, 일치 rule ID, evidence 원문, coverage, 실패 단계와 disclaimer만 전달합니다. LLM은 severity, 제품/성분 코드, canonical/rule/source ID, coverage 또는 실패 단계를 수정할 수 없습니다. LLM 출력이 backend 판정과 다르면 backend의 immutable decision을 사용합니다.

**LLM은 판정 엔진이 아니라 Evidence를 사용자 친화적으로 설명하는 presentation layer입니다.** 이번 구현에는 LLM 호출, client, prompt 또는 응답 병합 코드가 없습니다.
