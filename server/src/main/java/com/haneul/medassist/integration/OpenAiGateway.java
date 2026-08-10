package com.haneul.medassist.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** OpenAI 키와 원문 의료정보가 Android 또는 애플리케이션 로그에 노출되지 않게 하는 서버 전용 gateway. */
@Component
public class OpenAiGateway {
    private static final String CHAT_SYSTEM_PROMPT = """
            당신은 복약 정보를 쉽게 설명하는 보조 챗봇이다. 의사나 약사를 대체하지 않으며 진단, 처방,
            복용 중단·증량·감량을 지시하지 않는다. 약물 상호작용과 금기는 제공된 공식 근거 컨텍스트만 사용한다.
            근거가 없으면 안전하다고 추론하지 말고 확인할 수 없다고 답한다. 답변은 결론, 확인된 근거,
            사용자가 할 일 순서로 짧고 명확하게 작성한다. OCR 또는 사용자 입력이 불확실하면 제품명과 성분
            확인을 먼저 요청한다. 심각한 증상이나 응급 신호가 언급되면 즉시 119 또는 가까운 응급실 등
            지역 응급 도움을 받도록 안내한다. 모든 의료 의사결정은 담당 의사·약사와 확인하도록 한다.
            NO_KNOWN_ISSUE 또는 공식 상호작용 미발견은 조회된 데이터 범위의 결과일 뿐 안전 보증이 아니다.
            이 경우에도 '함께 복용해도 된다', '안전하다', '문제없다'고 표현하지 말고, 조회 범위 내 알려진
            상호작용이 확인되지 않았다고만 설명한 뒤 개인 상태와 다른 약을 포함해 의사·약사에게 확인하도록 한다.
            """;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String chatModel;
    private final String transcriptionModel;
    private final String summaryModel;
    private final long monthlyBudget;
    private final AtomicLong estimatedUsage = new AtomicLong();
    private final Map<UUID, ArrayDeque<Instant>> rateWindows = new ConcurrentHashMap<>();

    public OpenAiGateway(ObjectMapper mapper,
                         @Value("${app.openai.api-key:}") String apiKey,
                         @Value("${app.openai.chat-model:gpt-4o-mini}") String chatModel,
                         @Value("${app.openai.transcription-model:gpt-4o-mini-transcribe}") String transcriptionModel,
                         @Value("${app.openai.summary-model:gpt-4o-mini}") String summaryModel,
                         @Value("${app.openai.monthly-token-budget:100000}") long monthlyBudget) {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.transcriptionModel = transcriptionModel;
        this.summaryModel = summaryModel;
        this.monthlyBudget = monthlyBudget;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public MedicationQueryExtraction extractMedicationQueries(String userMessage) {
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("interactionQuestion").add("medicationQueries").add("ambiguousTerms");
        var properties = schema.putObject("properties");
        properties.putObject("interactionQuestion").put("type", "boolean");
        properties.putObject("medicationQueries").put("type", "array")
                .set("items", mapper.createObjectNode().put("type", "string"));
        properties.putObject("ambiguousTerms").put("type", "array")
                .set("items", mapper.createObjectNode().put("type", "string"));

        String instructions = """
                사용자의 한국어 복약 질문에서 실제로 언급된 의약품 제품명 또는 성분명과 함량만 추출한다.
                함께 복용, 병용, 상호작용, 같이 먹어도 되는지 묻는 질문이면 interactionQuestion을 true로 한다.
                medicationQueries에는 공공 의약품 검색에 사용할 수 있는 명칭을 사용자가 말한 그대로 넣고,
                해열제·감기약·진통제처럼 특정 제품이나 성분을 확정할 수 없는 표현은 ambiguousTerms에 넣는다.
                제품명 뒤 괄호에 적힌 성분은 그 제품의 설명이므로 별도 의약품으로 추가하지 않는다.
                예를 들어 '타이레놀정500밀리그람(아세트아미노펜)과 부루펜정200밀리그램(이부프로펜)'은
                medicationQueries 두 개만 반환한다.
                사용자가 말하지 않은 제품명이나 성분을 추측하거나 보충하지 않는다. 중복은 제거한다.
                """;
        JsonNode result = structuredResponse(
                chatModel,
                "medication_query_extraction",
                instructions,
                "[사용자 질문]\n" + userMessage,
                schema);
        var stringListType = mapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class);
        return new MedicationQueryExtraction(
                result.path("interactionQuestion").asBoolean(false),
                mapper.convertValue(result.path("medicationQueries"), stringListType),
                mapper.convertValue(result.path("ambiguousTerms"), stringListType));
    }

    public void streamChat(UUID userId, String officialContext, String userMessage, Consumer<String> delta) {
        requireConfigured();
        enforceQuota(userId, userMessage.length());
        try {
            var payload = mapper.createObjectNode();
            payload.put("model", chatModel);
            payload.put("stream", true);
            payload.put("store", false);
            payload.put("instructions", CHAT_SYSTEM_PROMPT);
            payload.put("input", "[공식 근거 컨텍스트]\n" + officialContext + "\n[사용자 질문]\n" + userMessage);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429) throw new ProviderException("OPENAI_RATE_LIMIT", true);
            if (response.statusCode() / 100 != 2) throw new ProviderException("OPENAI_PROVIDER_ERROR", true);
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ") || line.endsWith("[DONE]")) continue;
                    JsonNode event = mapper.readTree(line.substring(6));
                    if ("response.output_text.delta".equals(event.path("type").asText())) {
                        delta.accept(event.path("delta").asText());
                    }
                    if ("response.completed".equals(event.path("type").asText())) {
                        long total = event.path("response").path("usage").path("total_tokens").asLong(0);
                        estimatedUsage.addAndGet(total);
                    }
                }
            }
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("OPENAI_TIMEOUT_OR_NETWORK", true);
        }
    }

    /** 완성된 M4A 파일을 gpt-4o-mini 계열 전사 모델로 한국어 텍스트화한다. */
    public JsonNode transcribe(File audio) {
        requireConfigured();
        try {
            String boundary = "medassist-" + UUID.randomUUID();
            byte[] body = multipart(boundary, audio);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderException("OPENAI_RATE_LIMIT", true);
            if (response.statusCode() / 100 != 2) throw new ProviderException("TRANSCRIPTION_FAILED", true);
            return mapper.readTree(response.body());
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("TRANSCRIPTION_FAILED", true);
        }
    }

    private byte[] multipart(String boundary, File audio) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        writeField(output, boundary, "model", transcriptionModel);
        writeField(output, boundary, "response_format", "json");
        writeField(output, boundary, "language", "ko");
        writeField(output, boundary, "temperature", "0");
        writeField(output, boundary, "chunking_strategy", "auto");
        writeField(output, boundary, "include[]", "logprobs");
        writeField(output, boundary, "prompt", "들리는 한국어를 그대로 옮기세요. 들리지 않는 내용은 추측하거나 보충하지 마세요.");
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n" +
                "Content-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(Files.readAllBytes(audio.toPath()));
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    /** 전사문에서 의사/환자 발화를 문맥으로 구분하고 화면 계약에 맞는 진료 요약을 생성한다. */
    public JsonNode analyzeConsultation(UUID userId, String transcript) {
        requireConfigured();
        enforceQuota(userId, transcript.length());
        var itemSchema = mapper.createObjectNode();
        itemSchema.put("type", "object");
        itemSchema.putArray("required").add("text").add("evidenceIndexes");
        itemSchema.put("additionalProperties", false);
        var itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("text").put("type", "string");
        itemProperties.putObject("evidenceIndexes").put("type", "array")
                .set("items", mapper.createObjectNode().put("type", "integer"));

        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("dialogue").add("overallSummary").add("symptoms")
                .add("testsAndAssessment").add("prescriptionAndInstructions").add("followUps").add("uncertainties");
        var properties = schema.putObject("properties");
        var dialogueItem = mapper.createObjectNode();
        dialogueItem.put("type", "object");
        dialogueItem.put("additionalProperties", false);
        dialogueItem.putArray("required").add("speaker").add("text");
        var dialogueProperties = dialogueItem.putObject("properties");
        dialogueProperties.putObject("speaker").put("type", "string")
                .putArray("enum").add("의사").add("환자").add("확인 필요");
        dialogueProperties.putObject("text").put("type", "string");
        properties.putObject("dialogue").put("type", "array").set("items", dialogueItem);
        properties.putObject("overallSummary").put("type", "string");
        for (String field : new String[]{"symptoms", "testsAndAssessment", "prescriptionAndInstructions", "followUps", "uncertainties"}) {
            properties.putObject(field).put("type", "array").set("items", itemSchema.deepCopy());
        }

        String instructions = """
                당신은 한국어 진료 녹음을 정리하는 의료 기록 보조자다. 제공된 전사문에 있는 사실만 사용한다.
                대화 순서를 보존하면서 각 발화를 의사, 환자 또는 확인 필요로 분류한다. 문맥상 확실하지 않으면
                반드시 '확인 필요'를 사용하고, 화자나 진단을 지어내지 않는다. 요약은 전체 요약, 주요 증상,
                검사·진단, 처방 및 복용 안내, 추후 일정·주의사항, 확인 필요 항목으로 구성한다.
                진단과 처방은 의료진이 실제로 말한 내용만 기록한다. evidenceIndexes는 dialogue의 0부터 시작하는
                인덱스만 사용한다. dialogue의 각 text는 전사문의 연속된 원문을 그대로 복사해야 하며, 순서 변경,
                바꿔 쓰기, 문장 보충을 하지 않는다. dialogue의 text를 모두 이어 붙이면 전사문과 같아야 한다.
                모든 결과는 한국어로 작성한다.
                """;
        return structuredResponse(summaryModel, "consultation_record", instructions,
                "[진료 전사문]\n" + transcript, schema);
    }

    private JsonNode structuredResponse(String model, String name, String instructions, String input, JsonNode schema) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("model", model);
            payload.put("store", false);
            payload.put("instructions", instructions);
            payload.put("input", input);
            var format = payload.putObject("text").putObject("format");
            format.put("type", "json_schema");
            format.put("name", name);
            format.put("strict", true);
            format.set("schema", schema);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new ProviderException("OPENAI_RATE_LIMIT", true);
            if (response.statusCode() / 100 != 2) throw new ProviderException("SUMMARY_FAILED", true);
            JsonNode envelope = mapper.readTree(response.body());
            estimatedUsage.addAndGet(envelope.path("usage").path("total_tokens").asLong(0));
            for (JsonNode output : envelope.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        return mapper.readTree(content.path("text").asText());
                    }
                }
            }
            throw new ProviderException("SUMMARY_RESPONSE_EMPTY", true);
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException("SUMMARY_FAILED", true);
        }
    }

    private void writeField(java.io.ByteArrayOutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) throw new ProviderException("OPENAI_NOT_CONFIGURED", false);
    }

    private void enforceQuota(UUID userId, int promptCharacters) {
        if (estimatedUsage.get() + Math.max(1, promptCharacters / 3) > monthlyBudget) {
            throw new ProviderException("MONTHLY_BUDGET_EXCEEDED", false);
        }
        Instant cutoff = Instant.now().minusSeconds(60);
        ArrayDeque<Instant> window = rateWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) window.removeFirst();
            if (window.size() >= 10) throw new ProviderException("USER_RATE_LIMIT", true);
            window.addLast(Instant.now());
        }
    }

    public static class ProviderException extends RuntimeException {
        private final boolean retryable;
        public ProviderException(String code, boolean retryable) { super(code); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }

    public record MedicationQueryExtraction(boolean interactionQuestion,
                                            java.util.List<String> medicationQueries,
                                            java.util.List<String> ambiguousTerms) {}
}
