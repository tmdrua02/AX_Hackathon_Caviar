# Supplement Rule Import Schema

실행 가능한 JSON shape 정의는 `src/main/resources/config/supplement-rules/supplement-rule-catalog.schema.json`에 있다. Kotlin `SupplementRuleCatalogValidator`는 이 shape 검증과 참조·상태·기간·중복·manifest semantic 검증을 수행한다.

하위 호환성을 위해 네 section을 가진 단일 JSON을 유지하고 선택적 `manifest`를 추가했다.

```json
{
  "manifest": {
    "catalogVersion": "승인 시 명시",
    "schemaVersion": "1.0",
    "generatedAt": "ISO-8601 instant",
    "generatedBy": "작성자 식별값",
    "reviewer": "검수자 식별값",
    "reviewedAt": "ISO-8601 instant",
    "sourceFileChecksums": {"input.json": "SHA-256"},
    "recordCounts": {
      "sources": 0,
      "canonicalIngredients": 0,
      "productMappings": 0,
      "interactionRules": 0
    },
    "status": "VERIFIED",
    "contentChecksum": "manifest를 제외한 data section SHA-256"
  },
  "sources": [],
  "ingredients": [],
  "mappings": [],
  "rules": []
}
```

기본 `classpath:supplement-interaction-rules.json`에는 네 빈 배열만 있고 실제 의료 규칙은 없다. 기본 startup 정책은 VERIFIED manifest를 요구하므로 이 파일은 서버 기동을 막지 않지만 `catalogAvailable=false`가 된다. 개발·테스트 fixture만 명시적으로 `require-verified-manifest=false`를 사용할 수 있다.

## Record fields

| 배열 | 필수 필드 | 선택 필드 |
|---|---|---|
| `sources` | `id`, `authority`, `title`, `originalText`, `retrievedAt`, `verificationStatus`; `sourceUrl` 또는 `documentIdentifier` 중 하나 | `publishedAt`, `reviewedBy`, `reviewedAt`, `notes`, `sourceVersion` |
| `ingredients` | `id`, `canonicalName`, `displayName`, `active`, `sourceReferenceId`, `verificationStatus`, `createdAt`, `updatedAt` | `aliases`, `providerCode`, `category` |
| `mappings` | `id`, `statementNo`, `productName`, `supplementIngredientCanonicalId`, `ingredientDisplayName`, `mappingType`, `sourceField`, `sourceReferenceId`, `verificationStatus`, `createdAt`, `updatedAt` | `validFrom`, `validTo` |
| `rules` | `id`, `drugIngredientCode`, `drugIngredientName`, `supplementIngredientCanonicalId`, `severity`, `interactionType`, `userMessage`, `recommendation`, `sourceReferenceIds`, `verificationStatus`, `createdAt`, `updatedAt` | `mechanismSummary`, `validFrom`, `validTo`, `ruleVersion` |

VERIFIED source는 `reviewedBy`와 `reviewedAt`도 필수다. 저장 규칙 severity는 `AVOID_COMBINATION` 또는 `CAUTION`만 허용한다. `UNKNOWN`과 `NO_VERIFIED_RULE_FOUND`는 catalog 값이 아니라 분석 engine이 coverage로 계산한다.

## Semantic validation

- 모든 section ID는 catalog 전역에서 고유해야 한다.
- canonical alias는 대소문자·양끝 공백 정규화 후 중복될 수 없다.
- canonical의 source, mapping의 canonical/source, rule의 canonical/모든 source가 존재해야 한다.
- VERIFIED canonical/mapping/rule은 production eligible VERIFIED source와 canonical만 참조한다.
- VERIFIED mapping은 `UNVERIFIED_CANDIDATE`일 수 없다.
- `validTo < validFrom`, `updatedAt < createdAt`, active+RETIRED 충돌을 거부한다.
- 같은 약 성분코드–canonical ID 조합의 유효기간이 겹치는 VERIFIED rule을 거부한다.
- manifest schema version, record count, content checksum과 VERIFIED review metadata를 검증한다.

`CatalogValidationReport`에는 valid, version, section count, errors/warnings, duplicate IDs, missing references, invalid verification states/date ranges, duplicate active rules, checksum과 생성 시각이 들어간다. 오류 메시지는 데이터 품질만 설명하며 의료 내용을 만들지 않는다.

## Commands

```bash
./gradlew validateSupplementRuleCatalog \
  -PcatalogPath=/absolute/path/catalog.json \
  -PreportPath=/absolute/path/report.json

./gradlew buildVerifiedSupplementRuleCatalog \
  -PcatalogPath=/absolute/path/catalog.json \
  -Previewer="reviewer-id" \
  -PcatalogVersion="2026.08.1" \
  -PoutputPath=/absolute/path/verified-catalog.json
```

승인 명령은 오류가 하나라도 있으면 실패하고 output을 만들지 않는다. reviewer와 catalog version이 필수이며 input과 output은 다른 경로여야 한다. 원본 파일은 변경하지 않는다.

실제 의료 규칙을 추가하려면 출처 사용 권한, 원문, 공식 제품·성분 식별값, 약사/전문가 검수자, 검수 시각, 유효기간과 문구의 근거 범위를 먼저 확인해야 한다. 테스트 fixture는 `src/test/resources`에만 둔다.
