# Supplement Rule Import Schema

기본 resource는 `classpath:supplement-interaction-rules.json`이고 production 데이터는 모두 빈 배열이다. 운영 검수 파일은 `SUPPLEMENT_INTERACTION_RULES_RESOURCE`로 교체할 수 있다. 파일은 startup에 한 번 읽고 전체 무결성 검증에 실패하면 application startup을 실패시킨다.

```json
{
  "sources": [],
  "ingredients": [],
  "mappings": [],
  "rules": []
}
```

## Record fields

| 배열 | 필수 필드 | 선택 필드 |
|---|---|---|
| `sources` | `id`, `authority`, `title`, `originalText`, `retrievedAt`, `verificationStatus`; `sourceUrl` 또는 `documentIdentifier` 중 하나 | `publishedAt`, `reviewedBy`, `reviewedAt`, `notes` |
| `ingredients` | `id`, `canonicalName`, `displayName`, `active`, `sourceReferenceId`, `verificationStatus`, `createdAt`, `updatedAt` | `aliases`, `providerCode`, `category` |
| `mappings` | `id`, `statementNo`, `productName`, `supplementIngredientCanonicalId`, `ingredientDisplayName`, `mappingType`, `sourceField`, `sourceReferenceId`, `verificationStatus`, `createdAt`, `updatedAt` | `validFrom`, `validTo` |
| `rules` | `id`, `drugIngredientCode`, `drugIngredientName`, `supplementIngredientCanonicalId`, `severity`, `interactionType`, `userMessage`, `recommendation`, `sourceReferenceIds`, `verificationStatus`, `createdAt`, `updatedAt` | `mechanismSummary`, `validFrom`, `validTo` |

VERIFIED source는 `reviewedBy`와 `reviewedAt`도 필수다. 저장 규칙의 severity는 `AVOID_COMBINATION` 또는 `CAUTION`만 허용하며 `UNKNOWN`과 `NO_VERIFIED_RULE_FOUND`는 분석 엔진이 coverage로 계산한다. `statementNo`와 `drugIngredientCode`는 빈 값일 수 없고, 실제 분석 시 공식 제품/성분 provider 결과와 다시 일치시킨다.

## 참조 순서

- `sources[].id`는 전역에서 고유해야 한다.
- `ingredients[].sourceReferenceId`는 존재하는 source를 가리킨다.
- `mappings[].supplementIngredientCanonicalId`와 `sourceReferenceId`는 존재해야 한다.
- `rules[].supplementIngredientCanonicalId`와 모든 `sourceReferenceIds`는 존재해야 한다.
- VERIFIED canonical, mapping, rule은 VERIFIED source만 참조한다.
- VERIFIED mapping은 VERIFIED/active canonical을 가리키고 `UNVERIFIED_CANDIDATE`일 수 없다.
- VERIFIED rule은 VERIFIED/active canonical과 하나 이상의 VERIFIED source를 가져야 한다.

`validFrom`, `validTo`, `createdAt`, `updatedAt`, `publishedAt`, `retrievedAt`, `reviewedAt`은 ISO-8601 instant 문자열이다. `validTo < validFrom`, `updatedAt < createdAt`, 중복 ID, 동일 약 성분코드–canonical ID 조합에서 유효기간이 겹치는 VERIFIED 규칙은 거부한다.

테스트 규칙은 production resource에 넣지 않고 `src/test` fixture 또는 테스트 객체에서만 구성한다. 실제 의료 규칙을 추가하려면 출처 사용 권한, 원문, 약사/의료 검수자, 검수 시각, 유효기간과 문구의 근거 범위를 먼저 확인해야 한다.
