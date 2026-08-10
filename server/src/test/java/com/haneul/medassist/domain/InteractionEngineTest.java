package com.haneul.medassist.domain;

import com.haneul.medassist.api.ApiModels.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionEngineTest {
    private final InteractionEngine engine = new InteractionEngine();

    @Test
    void missingDurEvidenceIsUnknownNeverSafe() {
        var result = engine.analyze(med("새 약", "ingredient-a"), List.of(med("기존 약", "ingredient-b")),
                (a, b) -> Optional.empty());
        assertThat(result.results()).singleElement().extracting(InteractionResult::severity).isEqualTo(Severity.UNKNOWN);
        assertThat(result.results().getFirst().title()).contains("확인 불가");
    }

    @Test
    void sameNormalizedIngredientIsDuplicateWithPublicEvidence() {
        var result = engine.analyze(med("새 약", "acetaminophen"), List.of(med("타이레놀", "acetaminophen")),
                (a, b) -> Optional.empty());
        assertThat(result.results().getFirst().severity()).isEqualTo(Severity.DUPLICATE_OR_SIMILAR);
        assertThat(result.results().getFirst().evidence()).isNotEmpty();
    }

    @Test
    void prohibitedRequiresAndPreservesOfficialEvidence() {
        Evidence evidence = new Evidence("성분A", "성분B", "DUR_PROHIBITED", "식약처 DUR",
                "https://www.data.go.kr/data/15056780/openapi.do", "DUR-1", LocalDate.of(2026, 1, 1),
                Instant.now(), "병용금기 고시 내용", "PUBLIC_DATA");
        var result = engine.analyze(med("새 약", "a"), List.of(med("기존 약", "b")),
                (a, b) -> Optional.of(evidence));
        assertThat(result.results().getFirst().severity()).isEqualTo(Severity.PROHIBITED);
        assertThat(result.results().getFirst().evidence()).containsExactly(evidence);
        assertThat(result.coverage().successfulQueries()).isEqualTo(1);
    }

    @Test
    void normalizerIgnoresSpacingAndPunctuation() {
        assertThat(InteractionEngine.normalize(" Acetaminophen (정) ")).isEqualTo("acetaminophen정");
    }

    private Medication med(String name, String ingredient) {
        return new Medication(UUID.randomUUID(), name, ProductType.OTC_DRUG, null, null, true,
                List.of(new Ingredient(ingredient, ingredient, null, null, null)), "1정", "09:00", "식후", false, 0);
    }
}

