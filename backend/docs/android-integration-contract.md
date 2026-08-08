# Android Integration Contract

이 문서는 현재 backend 코드의 약–건강기능식품 분석 계약과 Android 연결 구현의 기준이다. Android는 공식 건강기능식품 후보의 `sttemntNo`와 확정된 의약품 `productCode`를 보존하여 이 endpoint를 호출한다.

## Connection

- 로컬 backend: `http://localhost:8081/`
- Android emulator: `http://10.0.2.2:8081/`
- endpoint: `POST /api/v1/supplement-interaction-checks`
- content type: `application/json`
- 처리 방식: 동기식. 기존 약–약 `POST/GET /api/v1/interaction-checks` polling 흐름과 별도다.

현재 Android `BuildConfig.API_BASE_URL`은 이미 emulator 주소를 사용하고 Retrofit의 `Json`은 `ignoreUnknownKeys=true`, `explicitNulls=false`다.

## Request

두 값은 사용자가 후보 확인을 마친 공식 식별값이어야 한다. 공백일 수 없고 최대 50자이며 `[A-Za-z0-9_-]`만 허용한다.

```json
{
  "medicationProductCode": "TEST_ITEM_SEQ",
  "supplementStatementNo": "TEST_STTEMNT_NO"
}
```

```kotlin
@Serializable
data class SupplementInteractionCheckRequest(
    val medicationProductCode: String,
    val supplementStatementNo: String,
)
```

## Response envelope

아래 필드는 모두 top-level 실제 응답 필드다. `medication`, `medicationOverview`, `supplement`만 조회 결과에 따라 null일 수 있다. 목록은 null 대신 빈 배열로 반환된다.

```kotlin
@Serializable
data class SupplementInteractionCheckResponse(
    val processingStatus: String,
    val severity: String,
    val message: String,
    val explanation: SupplementInteractionExplanationDto,
    val medication: MedicationInteractionSummaryDto? = null,
    val medicationOverview: DrugOverviewDto? = null,
    val supplement: SupplementInteractionProductDto? = null,
    val drugIngredients: List<DrugIngredientDto> = emptyList(),
    val supplementIngredients: List<SupplementIngredientDto> = emptyList(),
    val evaluatedPairs: List<SupplementInteractionPairDto> = emptyList(),
    val matchedRules: List<MatchedRuleDto> = emptyList(),
    val evidence: List<SupplementInteractionEvidenceDto> = emptyList(),
    val coverage: SupplementInteractionCoverageDto,
    val failedSteps: Set<String> = emptySet(),
    val catalogMetadata: SupplementInteractionCatalogMetadataDto,
    val disclaimer: String,
    val analyzedAt: String,
)

// severityValue는 알려진 wire 값만 enum으로 변환하고 미래 값은 UNKNOWN으로 처리한다.
```

Android DTO는 `processingStatus`, `severity`, `explanation.status`, `failedSteps`의 wire 문자열을 먼저 보존한다. 따라서 backend가 enum을 additive하게 확장해도 전체 응답 deserialize가 실패하지 않는다. UI는 `severityValue`와 `statusValue` 안전 변환값을 사용한다.

`severity`는 backend deterministic engine의 권위 값이다. `explanation`은 이 값을 바꾸지 않는다.

- `AVOID_COMBINATION`: VERIFIED 회피 규칙이 있음
- `CAUTION`: AVOID는 없고 VERIFIED 주의 규칙이 있음
- `NO_VERIFIED_RULE_FOUND`: 제품·성분·VERIFIED 매핑·전체 pair·catalog coverage가 완전하지만 일치 규칙이 없음. 안전하다는 뜻이 아님
- `UNKNOWN`: 제품, 성분, 매핑, catalog 또는 pair evaluation이 불완전함

## Product and ingredient DTOs

```kotlin
@Serializable
data class SourceMetadataDto(
    val name: String,
    val recordId: String,
    val retrievedAt: String,
    val providerReference: String,
)

@Serializable
data class MedicationInteractionSummaryDto(
    val productCode: String,
    val productName: String,
    val manufacturer: String? = null,
    val source: SourceMetadataDto,
)

@Serializable
data class SupplementInteractionProductDto(
    val statementNo: String,
    val productName: String,
    val manufacturer: String? = null,
    val registerDate: String? = null,
    val intakeMethod: String? = null,
    val intakeHint: String? = null,
    val mainFunction: String? = null,
    val baseStandard: String? = null,
    val productSource: SourceMetadataDto,
    val retrievedAt: String,
)

@Serializable
data class DrugIngredientDto(
    val providerCode: String? = null,
    val displayName: String,
    val normalizedName: String,
    val amount: Double? = null,
    val unit: String? = null,
    val source: SourceMetadataDto,
)

@Serializable
data class SupplementIngredientDto(
    val canonicalId: String,
    val canonicalName: String,
    val displayName: String,
    val providerCode: String? = null,
    val category: String? = null,
    val sourceReferenceId: String,
    val verificationStatus: EvidenceVerificationStatus,
)

@Serializable
enum class EvidenceVerificationStatus { DRAFT, PENDING_REVIEW, VERIFIED, REJECTED, RETIRED }
```

`DrugOverviewDto`는 다음 필드를 가진다: `productCode`, `productName`, nullable `manufacturer`, nullable `efficacy`, `usageMethod`, `warning`, `precautions`, `interactions`, `sideEffects`, `storageMethod`, `imageUrl`, `openDate`, `updateDate`, 그리고 non-null `source`, `coverage`. 각 의료 텍스트는 `{ "raw": String, "display": String }`이며 overview coverage는 `productResolved`, `overviewResolved`, `complete`다.

## Pair, rule, and evidence DTOs

```kotlin
@Serializable
data class SupplementInteractionPairDto(
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val evaluated: Boolean,
    val matchedRuleIds: List<String> = emptyList(),
    val errorCode: String? = null,
)

@Serializable
data class MatchedRuleDto(
    val id: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val severity: SupplementInteractionSeverity,
    val interactionType: String,
    val mechanismSummary: String? = null,
    val userMessage: String,
    val recommendation: String,
    val sourceReferenceIds: Set<String>,
    val verificationStatus: EvidenceVerificationStatus,
    val ruleVersion: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
)

@Serializable
data class SupplementInteractionEvidenceDto(
    val ruleId: String,
    val evidenceType: String,
    val sourceAuthority: String,
    val sourceReferenceId: String,
    val title: String,
    val sourceTitle: String,
    val originalText: String,
    val drugIngredientCode: String,
    val drugIngredientName: String,
    val supplementIngredientCanonicalId: String,
    val supplementIngredientName: String,
    val severity: SupplementInteractionSeverity,
    val verificationStatus: EvidenceVerificationStatus,
    val ruleVersion: String? = null,
    val sourceVersion: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
    val retrievedAt: String,
)
```

`interactionType` 값은 `BLEEDING_RISK`, `ABSORPTION_CHANGE`, `METABOLISM_CHANGE`, `EFFECT_INCREASE`, `EFFECT_DECREASE`, `DUPLICATE_EFFECT`, `BLOOD_PRESSURE_EFFECT`, `BLOOD_GLUCOSE_EFFECT`, `CENTRAL_NERVOUS_SYSTEM_EFFECT`, `ELECTROLYTE_EFFECT`, `LIVER_EFFECT`, `KIDNEY_EFFECT`, `OTHER`다. `sourceAuthority` 값은 `MFDS`, `FOOD_SAFETY_KOREA`, `DRUG_LABEL`, `PEER_REVIEWED_RESEARCH`, `OTHER_OFFICIAL`이다.

## Coverage, failures, catalog, and explanation

```kotlin
@Serializable
data class SupplementInteractionCoverageDto(
    val medicationResolved: Boolean,
    val medicationIngredientsExpected: Int,
    val medicationIngredientsResolved: Int,
    val medicationIngredientsComplete: Boolean,
    val supplementResolved: Boolean,
    val supplementIngredientMappingAvailable: Boolean,
    val supplementIngredientsExpected: Int,
    val supplementIngredientsVerified: Int,
    val totalPairs: Int,
    val evaluatedPairs: Int,
    val matchedPairs: Int,
    val failedPairs: Int,
    val ruleRepositoryAvailable: Boolean,
    val complete: Boolean,
    val percentage: Int,
)

@Serializable
data class SupplementInteractionCatalogMetadataDto(
    val available: Boolean,
    val verified: Boolean,
    val catalogVersion: String? = null,
    val schemaVersion: String? = null,
    val catalogChecksum: String? = null,
    val loadedAt: String,
    val sourceCount: Int,
    val canonicalIngredientCount: Int,
    val productMappingCount: Int,
    val interactionRuleCount: Int,
    val validationErrorCodes: List<String> = emptyList(),
)

@Serializable
data class SupplementInteractionExplanationDto(
    val status: String,
    val summary: String,
    val rationale: String,
    val consultationAdvice: String,
    val keyPoints: List<String> = emptyList(),
    val provider: String? = null,
    val model: String? = null,
)

// statusValue는 GENERATED/FALLBACK/UNAVAILABLE을 변환하고 미래 값은 UNAVAILABLE로 처리한다.
```

`GENERATED`는 구조화 LLM 응답이 검증과 safe-claim guard를 통과한 경우다. `FALLBACK`과 `UNAVAILABLE`도 정상적인 HTTP 성공 응답이며 deterministic `severity`, `coverage`, `failedSteps`, rule/source/product/ingredient ID에는 영향을 주지 않는다. UI는 explanation status를 의료 severity처럼 강조하지 말고, 세 상태 모두 `summary`, `rationale`, `consultationAdvice`를 표시할 수 있다.

`failedSteps` enum은 다음과 같다.

- `MEDICATION_NOT_FOUND`
- `MEDICATION_PRODUCT_LOOKUP_FAILED`
- `MEDICATION_INGREDIENT_LOOKUP_FAILED`
- `MEDICATION_INGREDIENT_CODE_MISSING`
- `MEDICATION_OVERVIEW_LOOKUP_FAILED`
- `SUPPLEMENT_NOT_FOUND`
- `SUPPLEMENT_PRODUCT_LOOKUP_FAILED`
- `SUPPLEMENT_INGREDIENT_MAPPING_MISSING`
- `SUPPLEMENT_INGREDIENT_MAPPING_LOOKUP_FAILED`
- `SUPPLEMENT_INGREDIENT_UNVERIFIED`
- `RULE_CATALOG_UNAVAILABLE`
- `RULE_CATALOG_INVALID`
- `RULE_LOOKUP_FAILED`
- `RULE_SOURCE_UNVERIFIED`
- `PAIR_EVALUATION_INCOMPLETE`

## Production-like empty catalog example

현재 production catalog record count는 모두 0이다. 공식 제품 두 개가 식별되더라도 VERIFIED 제품–원료 mapping이 없으면 핵심 결과는 다음과 같아야 한다. 생략된 제품 snapshot 값도 TEST fixture가 아니라 실제 provider 결과만 사용한다.

```json
{
  "processingStatus": "PARTIAL",
  "severity": "UNKNOWN",
  "message": "분석 근거를 완전히 확인하지 못했습니다(SUPPLEMENT_INGREDIENT_MAPPING_MISSING). 안전하다는 의미가 아닙니다.",
  "explanation": {
    "status": "UNAVAILABLE",
    "summary": "현재 확보된 데이터만으로 병용 여부를 충분히 확인할 수 없습니다.",
    "rationale": "확인하지 못한 단계: SUPPLEMENT_INGREDIENT_MAPPING_MISSING. 추가 의료 사실을 추론하지 않았습니다.",
    "consultationAdvice": "복용 전 의사 또는 약사와 상담하세요.",
    "keyPoints": [],
    "provider": "OPENAI",
    "model": "gpt-4o-mini"
  },
  "supplementIngredients": [],
  "evaluatedPairs": [],
  "matchedRules": [],
  "evidence": [],
  "coverage": {
    "supplementIngredientMappingAvailable": false,
    "complete": false
  },
  "failedSteps": ["SUPPLEMENT_INGREDIENT_MAPPING_MISSING"],
  "disclaimer": "이 결과는 정보 제공용이며 복용 전 의사 또는 약사와 상담하세요."
}
```

이 예시는 필드 의미를 보여 주는 축약 JSON이다. Retrofit DTO는 위 full response envelope를 사용한다. catalog manifest가 없는 운영 설정에서는 `RULE_CATALOG_UNAVAILABLE`이 추가될 수 있다. LLM이 활성화되어도 `UNKNOWN`은 바뀌지 않으며 “안전”, “복용 가능”, “문제 없음” 같은 확정 표현은 폐기되고 fallback으로 대체된다.

## Problem Details

형식 오류는 HTTP 400, 공공 provider 인증/응답 문제는 502 또는 503 계열의 RFC Problem Details다.

```kotlin
@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val timestamp: String? = null,
)
```

```json
{
  "type": "https://medassist.local/problems/validation-failed",
  "title": "요청값이 올바르지 않습니다.",
  "status": 400,
  "detail": "medicationProductCode: 의약품 품목기준코드를 입력해 주세요.",
  "instance": "/api/v1/supplement-interaction-checks",
  "code": "VALIDATION_FAILED",
  "timestamp": "2026-08-08T00:00:00Z"
}
```

Android repository는 HTTP 오류를 성공 `UNKNOWN`으로 직접 만들지 않는다. backend가 HTTP 200으로 반환한 `UNKNOWN`과 transport/Problem Details 오류를 별도 UI 상태로 유지한다.

## Android implementation

### CREATE

- `android/app/src/main/java/com/haneul/medassist/data/SupplementInteractionDtos.kt`
  - 위 request/response 및 nested DTO를 정의한다.
  - 기존 약–약 `Severity`, `InteractionCheck`, `Coverage`와 이름/의미가 다르므로 공유하지 않는다.
- `android/app/src/main/java/com/haneul/medassist/data/SupplementInteractionRemoteDataSource.kt`
  - 2xx, Problem Details, timeout, network, malformed response를 구분한다.
- `android/app/src/main/java/com/haneul/medassist/SupplementInteractionRequestStateMachine.kt`
  - Loading/Content/Error, 중복 호출 차단, stale response 무시를 담당한다.
- `android/app/src/main/java/com/haneul/medassist/ui/SupplementInteractionScreen.kt`
  - deterministic severity, Evidence, coverage, failedSteps, explanation, disclaimer를 표시한다.

### MODIFY

- `android/app/src/main/java/com/haneul/medassist/data/ApiService.kt`
  - 공식 건강기능식품 후보 검색과 `@POST("api/v1/supplement-interaction-checks")` 동기 suspend 호출을 추가했다.
- `android/app/src/main/java/com/haneul/medassist/data/MedAssistRepository.kt`
  - 공식 `ITEM_SEQ`와 `STTEMNT_NO`를 받는 별도 메서드를 추가한다.
  - provider/Problem Details 오류를 로컬 “안전” 결과로 바꾸지 않는다.
- `android/app/src/main/java/com/haneul/medassist/AppViewModel.kt`
  - 확정된 건강기능식품 `statementNo`와 `LoadState<SupplementInteractionCheckResponse>`를 보존한다.
  - 동기 endpoint이므로 기존 `Accepted`/polling 상태를 재사용하지 않는다.
- `android/app/src/main/java/com/haneul/medassist/ui/Screens.kt`
  - 공식 건강기능식품 후보 검색·확정 및 병용 확인 시작 UI를 추가했다.
- `android/app/src/main/java/com/haneul/medassist/ui/Navigation.kt`
  - 큰 payload를 route argument로 전달하지 않는 별도 `interaction/supplement-result` route를 추가했다.

### KEEP

- OCR/camera pipeline과 처방 draft 확인 흐름
- 기존 약–약 async `InteractionCheck` 및 polling 흐름
- `AppModule.kt`의 Retrofit/OkHttp/base URL 설정
- 기존 Material color/theme tokens

## UI mapping

- `AVOID_COMBINATION`: 기존 error 계열 강조와 강한 주의 문구
- `CAUTION`: 기존 warning 계열 강조와 주의 문구
- `NO_VERIFIED_RULE_FOUND`: “현재 검수된 주의 정보 없음”과 “안전 의미 아님”을 같이 표시
- `UNKNOWN`: “현재 데이터로 판단할 수 없음” 및 `failedSteps`를 사용자용 문구로 매핑
- `GENERATED`/`FALLBACK`/`UNAVAILABLE`: 디버그 상태로 전면 노출하지 않는다. 설명 본문은 표시하되 provider/model은 일반 사용자 화면에 표시하지 않는다.
- 모든 상태에서 backend `disclaimer`를 표시한다.

Android는 `message`와 `explanation`을 표시용으로 사용하되, severity 색상·아이콘은 반드시 top-level `severity`로 선택해야 한다. `explanation` 문장으로 severity를 재분류하지 않는다.

## Android state and security

`AppUiState.supplementInteraction`은 `Idle → Loading → Content/Error`로 전이한다. `UNKNOWN`과 `NO_VERIFIED_RULE_FOUND`는 모두 `Content`다. Loading 중 중복 요청은 거부하며 제품 선택이 바뀌면 진행 중 Job을 취소하고 generation token으로 늦게 도착한 응답을 무시한다.

OpenAI 및 공공데이터 키는 Android 설정·DTO·요청에 존재하지 않는다. OkHttp logging은 debug에서도 `BASIC`만 사용하고 release에서는 `NONE`이므로 body, Evidence, OCR 원문과 민감 header를 기록하지 않는다.

## Demonstration separation

- Production-like: 실제 공공 API + 빈 production catalog → `UNKNOWN`, mapping failure, incomplete coverage
- Test fixture: `TEST_DRUG_*`, `TEST_SUPPLEMENT_*`, test-only VERIFIED mapping/rule → `AVOID_COMBINATION` 또는 `CAUTION`

실제 제품을 test fixture rule과 연결하거나 test fixture를 production catalog로 복사하지 않는다.
