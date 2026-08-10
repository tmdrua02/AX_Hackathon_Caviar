package com.haneul.medassist.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public enum ProductType { PRESCRIPTION_DRUG, OTC_DRUG, HEALTH_SUPPLEMENT, UNKNOWN }
    public enum JobStatus { QUEUED, RUNNING, SUCCEEDED, FAILED, NEEDS_CONFIRMATION }
    public enum Severity { PROHIBITED, CAUTION, DUPLICATE_OR_SIMILAR, NO_KNOWN_ISSUE, UNKNOWN }

    public record Ingredient(String displayName, String normalizedName, String providerCode,
                             Double amount, String unit) {}

    public record Medication(UUID id, String name, ProductType productType, String productCode,
                             String manufacturer, boolean active, List<Ingredient> ingredients,
                             String dose, String time, String timing, boolean taken, long version) {}

    public record MedicationRequest(@NotBlank String name, @NotNull ProductType productType,
                                    String productCode, String manufacturer,
                                    @NotEmpty List<Ingredient> ingredients, String dose,
                                    String time, String timing, Boolean active, Long version) {}

    public record HomeResponse(String greeting, String subtitle, Counts counts,
                               List<Medication> todayMedications, String disclaimer) {}
    public record Counts(int total, int prescriptions, int supplements) {}
    public record DoseLogRequest(@NotNull Instant scheduledAt, @NotBlank String status,
                                 Instant takenAt, @NotNull Long expectedVersion) {}

    public record ProductCandidate(String name, String productCode, String manufacturer,
                                   int confidence, String source) {}
    public record PrescriptionDraft(UUID id, JobStatus status, String productName, String dose,
                                    int timesPerDay, int days, String timing, String manufacturer,
                                    String productCode, List<Ingredient> ingredients, String efficacy,
                                    int matchConfidence, String source, List<ProductCandidate> candidates,
                                    List<String> warnings) {}
    public record DraftUpdate(@NotBlank String productName, @NotBlank String dose,
                              int timesPerDay, int days, @NotBlank String timing,
                              String productCode, String manufacturer,
                              @NotEmpty List<Ingredient> ingredients) {}
    public record Accepted(UUID resourceId, UUID jobId, JobStatus status) {}

    public record InteractionRequest(@NotNull UUID newMedicationId,
                                     @NotEmpty List<UUID> existingMedicationIds) {}
    public record Evidence(String ingredientA, String ingredientB, String evidenceType,
                           String sourceName, String sourceUrl, String sourceRecordId,
                           LocalDate sourceDate, Instant retrievedAt, String originalSummary,
                           String sourceType) {}
    public record InteractionResult(UUID id, Medication newMedication, Medication existingMedication,
                                    Severity severity, String title, String easyExplanation,
                                    List<Evidence> evidence) {}
    public record Coverage(int identifiedIngredients, int successfulQueries,
                           int unidentifiedIngredients, boolean providerError) {}
    public record InteractionCheck(UUID id, UUID jobId, JobStatus status,
                                   List<InteractionResult> results, Coverage coverage,
                                   boolean saved, String disclaimer) {}

    public record Consultation(UUID id, String title, String hospitalName, Instant consultedAt,
                               long durationMs, JobStatus status, List<TranscriptSegment> transcript,
                               ConsultationSummary summary, String failureCode, String failureMessage) {}
    public record TranscriptSegment(UUID id, String speaker, long startMs, long endMs, String text) {}
    public record SummaryItem(String text, List<UUID> evidenceSegmentIds) {}
    public record ConsultationSummary(String overallSummary, List<SummaryItem> symptoms,
                                      List<SummaryItem> testsAndAssessment,
                                      List<SummaryItem> prescriptionAndInstructions,
                                      List<SummaryItem> followUps, List<SummaryItem> uncertainties) {}

    public record Reminder(UUID id, UUID medicationId, LocalTime localTime,
                           String weekdays, boolean enabled) {}
    public record ReminderRequest(@NotNull UUID medicationId, @NotNull LocalTime localTime,
                                  @NotBlank String weekdays, boolean enabled) {}
    public record ChatSession(UUID id, Instant createdAt) {}
    public record ChatMessageRequest(@NotBlank String message,
                                     @Size(max = 12000) String officialContext) {}
    public record ChatMessageAccepted(UUID messageId, String streamUrl) {}

    public record ApiError(String code, String message, Map<String, String> fieldErrors,
                           String traceId, boolean retryable) {}
}
