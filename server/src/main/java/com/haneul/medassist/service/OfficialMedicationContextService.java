package com.haneul.medassist.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haneul.medassist.integration.OpenAiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Resolves free-form medication questions to verified product codes before creating chat evidence. */
@Service
public class OfficialMedicationContextService {
    private static final int MAX_MEDICATIONS = 4;
    private static final int MAX_CANDIDATES = 3;
    private static final int MAX_EVIDENCE = 12;

    private final OpenAiGateway openAi;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String backendBaseUrl;

    public OfficialMedicationContextService(OpenAiGateway openAi,
                                            ObjectMapper mapper,
                                            @Value("${app.backend.base-url:http://localhost:8081}") String backendBaseUrl) {
        this.openAi = openAi;
        this.mapper = mapper;
        this.backendBaseUrl = backendBaseUrl.endsWith("/")
                ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                : backendBaseUrl;
    }

    public Resolution resolve(String prompt) {
        OpenAiGateway.MedicationQueryExtraction extraction;
        try {
            extraction = openAi.extractMedicationQueries(prompt);
        } catch (RuntimeException error) {
            return Resolution.direct("의약품명을 자동으로 확인하지 못했습니다. 정확한 제품명과 함량을 두 개 이상 입력해 주세요.");
        }
        if (!extraction.interactionQuestion()) return Resolution.notApplicable();

        List<String> queries = new ArrayList<>(new LinkedHashSet<>(extraction.medicationQueries().stream()
                .map(String::trim).filter(value -> value.length() >= 2).toList()));
        if (queries.size() > MAX_MEDICATIONS) queries = queries.subList(0, MAX_MEDICATIONS);
        if (!extraction.ambiguousTerms().isEmpty() || queries.size() < 2) {
            return Resolution.direct(ambiguousAnswer(queries, extraction.ambiguousTerms()));
        }

        List<ProductSearch> searches = new ArrayList<>();
        try {
            for (String query : queries) searches.add(search(query));
        } catch (RuntimeException error) {
            return Resolution.direct("공식 의약품 제품·성분 조회를 완료하지 못했습니다. 잠시 후 다시 시도하거나 의사·약사에게 확인해 주세요.");
        }

        List<ProductSelection> selected = searches.stream().map(this::selectExactProduct).toList();
        if (selected.stream().anyMatch(value -> value == null)) {
            return Resolution.direct(candidateConfirmationAnswer(searches));
        }

        try {
            JsonNode analysis = analyze(selected.getFirst(), selected.subList(1, selected.size()));
            return Resolution.context(formatOfficialContext(selected, analysis));
        } catch (RuntimeException error) {
            return Resolution.direct("제품과 성분은 확인했지만 공식 병용 분석을 완료하지 못했습니다. 안전하다는 의미가 아니므로 복용 전 의사·약사에게 확인해 주세요.");
        }
    }

    private ProductSearch search(String query) {
        var request = mapper.createObjectNode().put("query", query);
        JsonNode response = post("/api/v1/drug-products/search", request);
        return new ProductSearch(query, response.path("candidates"));
    }

    private ProductSelection selectExactProduct(ProductSearch search) {
        List<JsonNode> resolved = new ArrayList<>();
        search.candidates().forEach(candidate -> {
            if ("RESOLVED".equals(candidate.path("ingredientLookupStatus").asText())) resolved.add(candidate);
        });
        if (resolved.isEmpty()) return null;

        String compactQuery = compact(search.query());
        JsonNode exact = resolved.stream()
                .filter(candidate -> compact(candidate.path("productName").asText()).equals(compactQuery))
                .findFirst().orElse(null);
        if (exact == null && resolved.size() == 1 && resolved.getFirst().path("matchConfidence").asInt(0) >= 90) {
            exact = resolved.getFirst();
        }
        return exact == null ? null : toSelection(search.query(), exact);
    }

    private ProductSelection toSelection(String query, JsonNode candidate) {
        List<String> ingredients = new ArrayList<>();
        candidate.path("ingredients").forEach(value -> {
            String displayName = value.path("displayName").asText("").trim();
            if (!displayName.isBlank()) ingredients.add(displayName);
        });
        return new ProductSelection(
                query,
                candidate.path("productCode").asText(),
                candidate.path("productName").asText(),
                candidate.path("manufacturer").asText(""),
                ingredients);
    }

    private JsonNode analyze(ProductSelection added, List<ProductSelection> existing) {
        var request = mapper.createObjectNode();
        request.put("newMedicationProductCode", added.productCode());
        var codes = request.putArray("existingMedicationProductCodes");
        existing.forEach(value -> codes.add(value.productCode()));
        return post("/api/v1/drug-interaction-checks", request);
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(backendBaseUrl + path))
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("OFFICIAL_BACKEND_ERROR");
            return mapper.readTree(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OFFICIAL_BACKEND_INTERRUPTED");
        } catch (Exception error) {
            throw new IllegalStateException("OFFICIAL_BACKEND_UNAVAILABLE");
        }
    }

    private String formatOfficialContext(List<ProductSelection> products, JsonNode analysis) {
        StringBuilder context = new StringBuilder("공식 자동 성분·DUR 분석 결과\n");
        for (ProductSelection product : products) {
            context.append("확정 제품: ").append(product.productName())
                    .append(" / 품목기준코드 ").append(product.productCode());
            if (!product.manufacturer().isBlank()) context.append(" / 제조사 ").append(product.manufacturer());
            context.append("\n확인 성분: ")
                    .append(product.ingredients().isEmpty() ? "성분 미확인" : String.join(", ", product.ingredients()))
                    .append('\n');
        }
        context.append("전체 처리 상태: ").append(analysis.path("processingStatus").asText("UNKNOWN")).append('\n');
        int evidenceCount = 0;
        for (JsonNode result : analysis.path("results")) {
            context.append("조합: ")
                    .append(result.path("newMedication").path("productName").asText("확인 필요"))
                    .append(" / ")
                    .append(result.path("existingMedication").path("productName").asText("확인 필요"))
                    .append('\n');
            context.append("판정: ").append(result.path("severity").asText("UNKNOWN")).append('\n');
            context.append("공식 분석 설명: ").append(result.path("summary").asText("확인된 설명 없음")).append('\n');
            JsonNode coverage = result.path("coverage");
            if (!coverage.isMissingNode() && !coverage.isNull()) {
                context.append("분석 범위: 성분쌍 ").append(coverage.path("completedPairs").asInt())
                        .append('/').append(coverage.path("totalPairs").asInt())
                        .append(", 완전성 ").append(coverage.path("complete").asBoolean(false)).append('\n');
            }
            for (JsonNode evidence : result.path("evidence")) {
                if (evidenceCount++ >= MAX_EVIDENCE) break;
                context.append("공식 근거: ")
                        .append(evidence.path("ingredientA").asText()).append(" / ")
                        .append(evidence.path("ingredientB").asText()).append(" / ")
                        .append(evidence.path("evidenceType").asText()).append(" / ")
                        .append(evidence.path("sourceName").asText()).append(" / 기록 ")
                        .append(evidence.path("sourceRecordId").asText("없음")).append('\n');
            }
        }
        context.append("주의: ").append(analysis.path("disclaimer").asText(
                "정보 제공용이며 복용을 변경하기 전에 의사 또는 약사와 상담하세요."));
        return context.toString();
    }

    private String candidateConfirmationAnswer(List<ProductSearch> searches) {
        StringBuilder answer = new StringBuilder(
                "공식 성분 비교를 시작하려면 정확한 제품을 먼저 확인해야 합니다. 아래 후보 중 실제 복용 제품의 전체 제품명을 확인해 다시 질문해 주세요.\n");
        for (ProductSearch search : searches) {
            answer.append("\n입력: ").append(search.query()).append('\n');
            int count = 0;
            for (JsonNode candidate : search.candidates()) {
                if (count++ >= MAX_CANDIDATES) break;
                answer.append("- ").append(candidate.path("productName").asText("제품명 미확인"));
                String manufacturer = candidate.path("manufacturer").asText("");
                if (!manufacturer.isBlank()) answer.append(" · ").append(manufacturer);
                answer.append(" · 성분 ");
                List<String> ingredients = new ArrayList<>();
                candidate.path("ingredients").forEach(value -> ingredients.add(value.path("displayName").asText()));
                answer.append(ingredients.isEmpty() ? "미확인" : String.join(", ", ingredients)).append('\n');
            }
            if (search.candidates().isEmpty()) answer.append("- 일치하는 공식 제품 후보 없음\n");
        }
        return answer.append("\n제품 포장 또는 약 봉투의 제품명을 그대로 입력해 주세요. 후보를 임의로 선택해 안전 판정하지 않습니다.").toString();
    }

    private String ambiguousAnswer(List<String> queries, List<String> ambiguousTerms) {
        StringBuilder answer = new StringBuilder("실제 성분 비교를 위해 두 개 이상의 정확한 의약품 제품명과 함량이 필요합니다.");
        if (!queries.isEmpty()) answer.append("\n확인된 입력: ").append(String.join(", ", queries));
        if (!ambiguousTerms.isEmpty()) answer.append("\n제품을 특정할 수 없는 표현: ").append(String.join(", ", ambiguousTerms));
        return answer.append("\n예: 타이레놀정500밀리그람(아세트아미노펜)과 부루펜정200밀리그램(이부프로펜)을 함께 복용해도 되나요?").toString();
    }

    private String compact(String value) {
        return value.toLowerCase(Locale.KOREAN).replaceAll("[^가-힣a-z0-9]", "");
    }

    private record ProductSearch(String query, JsonNode candidates) {}
    private record ProductSelection(String query, String productCode, String productName,
                                    String manufacturer, List<String> ingredients) {}

    public record Resolution(boolean applicable, String officialContext, String directAnswer) {
        static Resolution notApplicable() { return new Resolution(false, null, null); }
        static Resolution context(String context) { return new Resolution(true, context, null); }
        static Resolution direct(String answer) { return new Resolution(true, null, answer); }
    }
}
