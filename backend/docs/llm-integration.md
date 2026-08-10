# LLM Integration

## Reused team implementation

`llm-merge-source/server`에서 OpenAI Responses API, 서버 환경변수 `OPENAI_API_KEY`·`OPENAI_CHAT_MODEL`, JDK HTTP timeout, `store=false`, strict JSON schema, 빈 응답 검증과 로그 비노출 원칙을 재사용했습니다. 기존 소스는 Java 21/Jackson 2의 별도 Spring 애플리케이션이므로 현재 Kotlin/Spring Boot 4/Jackson 3 package에 adapter로 이식했습니다.

자유형 `ChatSafetyService`, 음성 전사·진료 요약, DemoStore/controller/Android, 이름 기반 별도 `InteractionEngine`은 사용하지 않습니다. 특히 팀원 engine의 severity와 mock evidence ID는 현재 공식 코드 기반 deterministic engine과 충돌하므로 병합하지 않습니다.

## Runtime flow

1. `SupplementInteractionAnalysisService.analyze()`가 authoritative 의료 분석을 완료합니다.
2. `toExplanationRequest()`가 immutable DTO를 생성합니다.
3. `SupplementInteractionExplanationService`가 설정된 경우에만 OpenAI client를 호출합니다.
4. structured output과 안전 guard를 통과하면 `GENERATED`, 그 외에는 `FALLBACK` 또는 `UNAVAILABLE` 설명을 생성합니다.
5. REST 응답은 원래 analysis를 그대로 유지하고 비권위 explanation만 추가합니다.

LLM 입력은 medication/supplement snapshot, 공식 약 성분, VERIFIED canonical 원료와 rule, Evidence 원문, coverage, failedSteps, immutableDecision, catalog metadata, disclaimer로 제한됩니다. web search/grounding과 외부 지식 검색은 사용하지 않습니다.

## Output and guard

OpenAI 출력 schema는 `summary`, `rationale`, `consultationAdvice`, 최대 5개의 `keyPoints`만 허용합니다. severity, safe boolean, product/ingredient/canonical/rule/source ID와 coverage를 출력 authority로 허용하지 않습니다.

blank, 길이 초과, malformed/empty JSON은 fallback입니다. `UNKNOWN` 또는 `NO_VERIFIED_RULE_FOUND`에서 “안전합니다”, “같이 드셔도 됩니다”, “문제없습니다”, “복용 가능합니다” 등 확정적 안전 표현이 있으면 전체 LLM 결과를 폐기합니다. production catalog가 비어 mapping이 없으면 deterministic `UNKNOWN`을 그대로 유지합니다.

## Configuration and resilience

- provider: OpenAI Responses API
- default model: `gpt-4o-mini`
- key: `OPENAI_API_KEY`
- model override: `OPENAI_CHAT_MODEL`
- optional endpoint: `OPENAI_BASE_URL`, `OPENAI_RESPONSES_PATH`
- timeout/retry: `OPENAI_CONNECT_TIMEOUT`, `OPENAI_READ_TIMEOUT`, `OPENAI_MAX_RETRIES`, `OPENAI_RETRY_BACKOFF`
- bulkhead/circuit: LLM 전용 인스턴스이며 공공데이터 executor와 공유하지 않음

401/403은 재시도하지 않고 429, timeout, 5xx만 제한적으로 재시도합니다. 키가 없어도 서버가 시작되고 deterministic 결과와 `UNAVAILABLE` fallback을 반환합니다.

전체 prompt, Evidence 원문, request/response DTO, API key는 로그에 기록하지 않습니다. adapter는 provider/model과 안전한 실패 분류만 보유하며 exception에 키나 응답 body를 넣지 않습니다.

## Tests

기본 테스트는 fake client와 MockWebServer만 사용합니다. 실제 호출은 합성 `UNKNOWN` fixture만 사용하는 opt-in task입니다.

```bash
./gradlew test
OPENAI_API_KEY='server-only-key' ./gradlew externalLlmTest
```

실제 테스트도 전체 prompt, Evidence와 응답을 출력하지 않습니다.
