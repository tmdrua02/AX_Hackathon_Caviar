package com.haneul.medassist.domain;

import com.haneul.medassist.api.ApiModels.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class InteractionEngine {
    public interface EvidenceProvider {
        Optional<Evidence> find(String normalizedA, String normalizedB);
    }

    public record Analysis(List<InteractionResult> results, Coverage coverage) {}

    public Analysis analyze(Medication added, List<Medication> existing, EvidenceProvider provider) {
        var results = new ArrayList<InteractionResult>();
        int identified = added.ingredients().size();
        int queries = 0;
        int unidentified = 0;

        for (Medication current : existing) {
            identified += current.ingredients().size();
            Severity severity = Severity.UNKNOWN;
            var evidence = new ArrayList<Evidence>();
            boolean everyIngredientIdentified = !added.ingredients().isEmpty() && !current.ingredients().isEmpty();
            int pairQueries = 0;
            if (!everyIngredientIdentified) unidentified++;

            for (Ingredient a : added.ingredients()) {
                for (Ingredient b : current.ingredients()) {
                    String left = normalize(a.normalizedName());
                    String right = normalize(b.normalizedName());
                    if (left.isBlank() || right.isBlank()) {
                        unidentified++;
                        continue;
                    }
                    if (left.equals(right)) {
                        severity = max(severity, Severity.DUPLICATE_OR_SIMILAR);
                        evidence.add(new Evidence(a.displayName(), b.displayName(), "SAME_INGREDIENT",
                                "식품의약품안전처 의약품 제품 허가정보",
                                "https://www.data.go.kr/data/15095677/openapi.do", "MOCK-SAME-001",
                                LocalDate.of(2026, 7, 1), Instant.now(),
                                "두 제품에서 같은 표준화 성분명이 확인됨", "PUBLIC_DATA"));
                    }
                    Optional<Evidence> official = provider.find(left, right);
                    if (official.isPresent()) {
                        queries++;
                        pairQueries++;
                        evidence.add(official.get());
                        severity = max(severity, severityFrom(official.get().evidenceType()));
                    }
                }
            }

            if (severity == Severity.UNKNOWN && everyIngredientIdentified && pairQueries > 0) {
                severity = Severity.NO_KNOWN_ISSUE;
            }
            String title = switch (severity) {
                case PROHIBITED -> "동시복용 금기 정보 확인";
                case CAUTION -> "복용 전 전문가 확인 필요";
                case DUPLICATE_OR_SIMILAR -> "동일 성분 또는 유사 효능 가능성";
                case NO_KNOWN_ISSUE -> "확인된 공공데이터 범위 내 특이사항 없음";
                case UNKNOWN -> "확인 불가 · 전문가 확인 필요";
            };
            String explanation = severity == Severity.UNKNOWN
                    ? "공신력 데이터로 이 조합을 충분히 확인하지 못했습니다. 안전하다는 의미가 아닙니다."
                    : "표시된 공공데이터 근거를 확인하고 복용 변경 전 의사·약사와 상담하세요.";
            results.add(new InteractionResult(UUID.randomUUID(), added, current, severity,
                    title, explanation, List.copyOf(evidence)));
        }
        return new Analysis(List.copyOf(results), new Coverage(identified, queries, unidentified, false));
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s()\\[\\]·_/-]", "");
    }

    private static Severity severityFrom(String type) {
        return switch (type) {
            case "DUR_PROHIBITED" -> Severity.PROHIBITED;
            case "DUR_CAUTION" -> Severity.CAUTION;
            default -> Severity.UNKNOWN;
        };
    }

    private static Severity max(Severity a, Severity b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(Severity value) {
        return switch (value) {
            case PROHIBITED -> 5;
            case CAUTION -> 4;
            case DUPLICATE_OR_SIMILAR -> 3;
            case NO_KNOWN_ISSUE -> 2;
            case UNKNOWN -> 1;
        };
    }
}
