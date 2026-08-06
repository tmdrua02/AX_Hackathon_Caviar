package com.haneul.medassist.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.haneul.medassist.api.ApiModels.Accepted;
import com.haneul.medassist.api.ApiModels.Consultation;
import com.haneul.medassist.api.ApiModels.ConsultationSummary;
import com.haneul.medassist.api.ApiModels.JobStatus;
import com.haneul.medassist.api.ApiModels.SummaryItem;
import com.haneul.medassist.api.ApiModels.TranscriptSegment;
import com.haneul.medassist.integration.OpenAiGateway;
import com.haneul.medassist.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Stores recordings and performs transcription + structured summarization away from the Android client. */
@Service
public class ConsultationProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ConsultationProcessingService.class);
    private final DemoStore store;
    private final ObjectStorage storage;
    private final OpenAiGateway openAi;
    private final Map<UUID, ObjectStorage.StoredObject> audioObjects = new ConcurrentHashMap<>();

    public ConsultationProcessingService(DemoStore store, ObjectStorage storage, OpenAiGateway openAi) {
        this.store = store;
        this.storage = storage;
        this.openAi = openAi;
    }

    public Accepted create(MultipartFile audio, String title, String hospital, Instant consultedAt, long durationMs)
            throws IOException {
        Accepted accepted = store.createConsultation(title, hospital, consultedAt, durationMs);
        ObjectStorage.StoredObject object = storage.put(accepted.resourceId().toString(),
                audio.getContentType() == null ? "audio/mp4" : audio.getContentType(), audio.getInputStream());
        audioObjects.put(accepted.resourceId(), object);
        processAsync(accepted.resourceId());
        return accepted;
    }

    public byte[] audio(UUID consultationId) throws IOException {
        store.consultation(consultationId);
        ObjectStorage.StoredObject object = audioObjects.get(consultationId);
        if (object == null) throw new java.util.NoSuchElementException("녹음 파일을 찾을 수 없습니다.");
        return storage.read(consultationId.toString(), object.objectKey());
    }

    public Accepted retry(UUID consultationId) {
        store.consultation(consultationId);
        if (!audioObjects.containsKey(consultationId)) {
            throw new java.util.NoSuchElementException("다시 분석할 녹음 파일을 찾을 수 없습니다.");
        }
        store.updateConsultation(consultationId, JobStatus.QUEUED, List.of(), null);
        processAsync(consultationId);
        return new Accepted(consultationId, UUID.randomUUID(), JobStatus.QUEUED);
    }

    public void delete(UUID consultationId) throws IOException {
        ObjectStorage.StoredObject object = audioObjects.remove(consultationId);
        if (object != null) storage.delete(consultationId.toString(), object.objectKey());
        store.deleteConsultation(consultationId);
    }

    private void processAsync(UUID consultationId) {
        CompletableFuture.runAsync(() -> process(consultationId));
    }

    private void process(UUID consultationId) {
        Consultation consultation = store.consultation(consultationId);
        store.updateConsultation(consultationId, JobStatus.RUNNING, List.of(), null);
        Path temporary = null;
        try {
            if (!openAi.isConfigured()) throw new OpenAiGateway.ProviderException("OPENAI_NOT_CONFIGURED", false);
            temporary = Files.createTempFile("medassist-consultation-", ".m4a");
            Files.write(temporary, audio(consultationId));
            String rawTranscript = openAi.transcribe(temporary.toFile()).path("text").asText("").trim();
            if (rawTranscript.isBlank()) throw new OpenAiGateway.ProviderException("TRANSCRIPTION_EMPTY", true);
            JsonNode result = openAi.analyzeConsultation(DemoStore.DEMO_USER, rawTranscript);
            List<TranscriptSegment> segments = toSegments(result.path("dialogue"), consultation.durationMs());
            ConsultationSummary summary = toSummary(result, segments);
            store.updateConsultation(consultationId, JobStatus.SUCCEEDED, segments, summary);
        } catch (Exception error) {
            String code = error instanceof OpenAiGateway.ProviderException ? error.getMessage() : "PROCESSING_FAILED";
            String message = failureMessage(code);
            log.error("Consultation processing failed. consultationId={}, code={}", consultationId, code, error);
            store.failConsultation(consultationId, code, message);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private String failureMessage(String code) {
        return switch (code) {
            case "OPENAI_NOT_CONFIGURED" -> "서버에서 OpenAI API 키를 읽지 못했습니다.";
            case "OPENAI_RATE_LIMIT", "USER_RATE_LIMIT" -> "요청이 많아 OpenAI 처리가 제한되었습니다. 잠시 후 다시 시도해 주세요.";
            case "MONTHLY_BUDGET_EXCEEDED" -> "서버에 설정된 OpenAI 사용 한도에 도달했습니다.";
            case "TRANSCRIPTION_FAILED" -> "녹음 파일을 음성 기록으로 변환하지 못했습니다.";
            case "TRANSCRIPTION_EMPTY" -> "녹음에서 분석할 수 있는 음성을 찾지 못했습니다.";
            case "SUMMARY_FAILED", "SUMMARY_RESPONSE_EMPTY" -> "음성 기록은 처리됐지만 진료 요약을 생성하지 못했습니다.";
            case "OPENAI_TIMEOUT_OR_NETWORK", "OPENAI_PROVIDER_ERROR" -> "OpenAI 서버 연결에 실패했습니다.";
            default -> "진료 기록 처리 중 예상하지 못한 오류가 발생했습니다.";
        };
    }

    private List<TranscriptSegment> toSegments(JsonNode dialogue, long durationMs) {
        List<JsonNode> rows = new ArrayList<>();
        dialogue.forEach(rows::add);
        if (rows.isEmpty()) throw new IllegalArgumentException("대화 구간이 비어 있습니다.");
        long totalWeight = rows.stream().mapToLong(row -> Math.max(1, row.path("text").asText().length())).sum();
        long cursor = 0;
        List<TranscriptSegment> segments = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            JsonNode row = rows.get(index);
            long weight = Math.max(1, row.path("text").asText().length());
            long end = index == rows.size() - 1 ? durationMs : Math.min(durationMs, cursor + durationMs * weight / totalWeight);
            String speaker = switch (row.path("speaker").asText()) {
                case "의사" -> "의사";
                case "환자" -> "환자";
                default -> "확인 필요";
            };
            segments.add(new TranscriptSegment(UUID.randomUUID(), speaker, cursor, Math.max(cursor, end),
                    row.path("text").asText()));
            cursor = end;
        }
        return List.copyOf(segments);
    }

    private ConsultationSummary toSummary(JsonNode result, List<TranscriptSegment> segments) {
        List<SummaryItem> uncertainties = new ArrayList<>(toItems(result.path("uncertainties"), segments));
        uncertainties.add(new SummaryItem("의사·환자 구분과 발화 시간은 AI가 문맥과 녹음 길이로 추정했습니다. 원음으로 확인해 주세요.",
                segments.stream().map(TranscriptSegment::id).toList()));
        return new ConsultationSummary(
                result.path("overallSummary").asText("진료 내용을 요약하지 못했습니다."),
                toItems(result.path("symptoms"), segments),
                toItems(result.path("testsAndAssessment"), segments),
                toItems(result.path("prescriptionAndInstructions"), segments),
                toItems(result.path("followUps"), segments),
                List.copyOf(uncertainties));
    }

    private List<SummaryItem> toItems(JsonNode values, List<TranscriptSegment> segments) {
        List<SummaryItem> items = new ArrayList<>();
        for (JsonNode value : values) {
            List<UUID> evidence = new ArrayList<>();
            for (JsonNode index : value.path("evidenceIndexes")) {
                int position = index.asInt(-1);
                if (position >= 0 && position < segments.size()) evidence.add(segments.get(position).id());
            }
            String text = value.path("text").asText("").trim();
            if (!text.isBlank()) items.add(new SummaryItem(text, List.copyOf(evidence)));
        }
        return List.copyOf(items);
    }
}
