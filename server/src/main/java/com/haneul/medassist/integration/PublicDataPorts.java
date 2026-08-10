package com.haneul.medassist.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 공급자 DTO가 도메인으로 새지 않도록 유지하는 공공데이터 포트 모음. */
public final class PublicDataPorts {
    private PublicDataPorts() {}

    public record SourceMetadata(String sourceName, String sourceUrl, String recordId,
                                 LocalDate sourceDate, Instant retrievedAt) {}
    public record ProductMatch(String productCode, String productName, String manufacturer,
                               int confidence, List<IngredientMatch> ingredients, SourceMetadata source) {}
    public record IngredientMatch(String providerCode, String displayName, String normalizedName) {}
    public record DurRelation(String ingredientA, String ingredientB, String durType,
                              String prohibitionSummary, SourceMetadata source) {}

    public interface DurIngredientPort { Optional<DurRelation> relation(String ingredientA, String ingredientB); }
    public interface DrugApprovalPort { List<ProductMatch> searchByNormalizedName(String normalizedProductName); }
    public interface EasyDrugPort { Optional<String> usageAndCautions(String productCode); }
    public interface HealthSupplementProductPort { List<ProductMatch> searchProduct(String normalizedProductName); }
    public interface HealthSupplementMaterialPort { List<IngredientMatch> materials(String productCode); }
}

