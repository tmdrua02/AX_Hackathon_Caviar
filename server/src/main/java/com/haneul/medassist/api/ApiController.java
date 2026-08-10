package com.haneul.medassist.api;

import com.haneul.medassist.api.ApiModels.*;
import com.haneul.medassist.service.ChatSafetyService;
import com.haneul.medassist.service.ConsultationProcessingService;
import com.haneul.medassist.service.DemoStore;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final DemoStore store;
    private final ChatSafetyService chat;
    private final ConsultationProcessingService consultations;
    private final Executor chatExecutor;

    public ApiController(DemoStore store, ChatSafetyService chat, ConsultationProcessingService consultations,
                         @Qualifier("chatExecutor") Executor chatExecutor) {
        this.store = store;
        this.chat = chat;
        this.consultations = consultations;
        this.chatExecutor = chatExecutor;
    }

    @GetMapping("/home")
    HomeResponse home(@RequestHeader(name = "X-Demo-User-Id", required = false) String user) {
        verifyDemoUser(user);
        return store.home();
    }

    @GetMapping("/medications")
    List<Medication> medications(@RequestParam(defaultValue = "true") boolean active) { return store.medications(active); }

    @PostMapping("/medications")
    ResponseEntity<Medication> createMedication(@Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(store.createMedication(request));
    }

    @PatchMapping("/medications/{id}")
    Medication updateMedication(@PathVariable UUID id, @Valid @RequestBody MedicationRequest request) {
        return store.updateMedication(id, request);
    }

    @DeleteMapping("/medications/{id}")
    ResponseEntity<Void> deleteMedication(@PathVariable UUID id) {
        store.deleteMedication(id); return ResponseEntity.noContent().build();
    }

    @PostMapping("/medications/{id}/dose-logs")
    Medication logDose(@PathVariable UUID id, @Valid @RequestBody DoseLogRequest request) {
        return store.logDose(id, request);
    }

    @PostMapping(value = "/prescription-drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PrescriptionDraft> createDraft(@RequestPart MultipartFile frontImage,
                                                   @RequestPart MultipartFile backImage,
                                                   @RequestPart(required = false) String clientOcrText) {
        validateImage(frontImage, "앞면"); validateImage(backImage, "뒷면");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(store.createDraft());
    }

    @GetMapping("/prescription-drafts/{id}")
    PrescriptionDraft draft(@PathVariable UUID id) { return store.draft(id); }

    @PatchMapping("/prescription-drafts/{id}")
    PrescriptionDraft updateDraft(@PathVariable UUID id, @Valid @RequestBody DraftUpdate request) {
        return store.updateDraft(id, request);
    }

    @PostMapping("/prescription-drafts/{id}/confirm")
    Medication confirmDraft(@PathVariable UUID id) { return store.confirmDraft(id); }

    @PostMapping("/interaction-checks")
    ResponseEntity<Accepted> createCheck(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                         @Valid @RequestBody InteractionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(store.createCheck(key, request));
    }

    @GetMapping("/interaction-checks/{id}")
    InteractionCheck check(@PathVariable UUID id) { return store.check(id); }

    @PostMapping("/interaction-checks/{id}/save")
    InteractionCheck saveCheck(@PathVariable UUID id) { return store.saveCheck(id); }

    @GetMapping("/interaction-checks/{checkId}/results/{resultId}/source")
    Evidence source(@PathVariable UUID checkId, @PathVariable UUID resultId) {
        return store.check(checkId).results().stream().filter(r -> r.id().equals(resultId))
                .findFirst().flatMap(r -> r.evidence().stream().findFirst())
                .orElseThrow(() -> new java.util.NoSuchElementException("근거를 찾을 수 없습니다."));
    }

    @GetMapping("/consultations")
    List<Consultation> consultations() { return store.consultations(); }

    @PostMapping(value = "/consultations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Accepted> createConsultation(@RequestPart MultipartFile audio,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                @RequestPart String title,
                                                @RequestPart(required = false) String hospitalName,
                                                @RequestPart String consultedAt,
                                                @RequestPart String durationMs) throws IOException {
        validateAudio(audio);
        if (title.isBlank() || title.length() > 120) throw new IllegalArgumentException("진료 기록 제목을 확인해 주세요.");
        long parsedDuration = Long.parseLong(durationMs);
        if (parsedDuration <= 0 || parsedDuration > 12 * 60 * 60 * 1000L) {
            throw new IllegalArgumentException("녹음 길이를 확인해 주세요.");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(consultations.create(audio, key, title, hospitalName,
                Instant.parse(consultedAt), parsedDuration));
    }

    @GetMapping("/consultations/{id}")
    Consultation consultation(@PathVariable UUID id) { return store.consultation(id); }

    @GetMapping("/consultations/{id}/audio")
    ResponseEntity<byte[]> audio(@PathVariable UUID id, @RequestHeader(name = "Range", required = false) String range)
            throws IOException {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mp4"))
                .header("Accept-Ranges", "bytes").body(consultations.audio(id));
    }

    @PostMapping("/consultations/{id}/retry")
    ResponseEntity<Accepted> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(consultations.retry(id));
    }

    @DeleteMapping("/consultations/{id}")
    ResponseEntity<Void> deleteConsultation(@PathVariable UUID id) throws IOException {
        consultations.delete(id); return ResponseEntity.noContent().build();
    }

    @GetMapping("/reminders")
    List<Reminder> reminders() { return store.reminders(); }
    @PostMapping("/reminders")
    ResponseEntity<Reminder> createReminder(@Valid @RequestBody ReminderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(store.saveReminder(null, request));
    }
    @PatchMapping("/reminders/{id}")
    Reminder updateReminder(@PathVariable UUID id, @Valid @RequestBody ReminderRequest request) {
        return store.saveReminder(id, request);
    }
    @DeleteMapping("/reminders/{id}")
    ResponseEntity<Void> deleteReminder(@PathVariable UUID id) {
        store.deleteReminder(id); return ResponseEntity.noContent().build();
    }

    @PostMapping("/chat/sessions")
    ResponseEntity<ChatSession> createChat() { return ResponseEntity.status(HttpStatus.CREATED).body(store.createChat()); }

    @PostMapping("/chat/sessions/{id}/messages")
    ResponseEntity<ChatMessageAccepted> chatMessage(@PathVariable UUID id,
                                                    @Valid @RequestBody ChatMessageRequest request) {
        UUID message = store.addChatMessage(id, request.message(), request.officialContext());
        return ResponseEntity.accepted().body(new ChatMessageAccepted(message, "/api/v1/chat/sessions/" + id + "/stream"));
    }

    @GetMapping(value = "/chat/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable UUID id) {
        String prompt = store.lastPrompt(id);
        String officialContext = store.lastOfficialContext(id);
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    chat.stream(DemoStore.DEMO_USER, officialContext, prompt, part -> {
                        try { emitter.send(SseEmitter.event().name("delta").data(Map.of("text", part))); }
                        catch (IOException exception) { throw new RuntimeException(exception); }
                    });
                    emitter.send(SseEmitter.event().name("done").data(Map.of("status", "completed")));
                    emitter.complete();
                } catch (Exception error) { emitter.completeWithError(error); }
            }, chatExecutor);
        } catch (RejectedExecutionException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private void verifyDemoUser(String user) {
        if (user != null && !user.equals(DemoStore.DEMO_USER.toString())) {
            throw new java.util.NoSuchElementException("사용자 리소스를 찾을 수 없습니다.");
        }
    }
    private void validateImage(MultipartFile file, String side) {
        String type = file.getContentType();
        if (file.isEmpty() || type == null || !(type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/webp"))) {
            throw new IllegalArgumentException(side + " 이미지는 JPEG, PNG 또는 WEBP만 사용할 수 있습니다.");
        }
    }
    private void validateAudio(MultipartFile file) {
        String type = file.getContentType();
        if (file.isEmpty() || type == null || !(type.equals("audio/mp4") || type.equals("audio/m4a")
                || type.equals("audio/x-m4a") || type.equals("audio/aac"))) {
            throw new IllegalArgumentException("M4A/AAC 오디오만 업로드할 수 있습니다.");
        }
    }
}
