package com.haneul.medassist.service;

import com.haneul.medassist.api.ApiModels.*;
import com.haneul.medassist.domain.InteractionEngine;
import com.haneul.medassist.persistence.PersistentStateService;
import com.haneul.medassist.storage.ObjectStorage;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoStore {
    public static final UUID DEMO_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_DEMO_CONSULTATION = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final Map<UUID, Medication> medications = new ConcurrentHashMap<>();
    private final Map<UUID, PrescriptionDraft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, InteractionCheck> checks = new ConcurrentHashMap<>();
    private final Map<UUID, Consultation> consultations = new ConcurrentHashMap<>();
    private final Map<UUID, Reminder> reminders = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> chatPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, String> chatOfficialContexts = new ConcurrentHashMap<>();
    private final Map<String, Accepted> idempotency = new ConcurrentHashMap<>();
    private final Map<UUID, ObjectStorage.StoredObject> audioObjects = new ConcurrentHashMap<>();
    private final InteractionEngine engine = new InteractionEngine();
    private final PersistentStateService persistence;

    public DemoStore(PersistentStateService persistence) {
        this.persistence = persistence;
    }

    @PostConstruct
    void initialize() {
        restore();
        removeLegacyDemoConsultation();
        ensureOfficialDemoMedication(
                "11111111-1111-1111-1111-111111111111",
                "타이레놀정500밀리그람(아세트아미노펜)", ProductType.OTC_DRUG,
                "202106092", "켄뷰코리아판매유한회사",
                "아세트아미노펜", "acetaminophen", "M040353", 500.0, "밀리그램",
                "1정", "09:00", "식후", false);
        ensureOfficialDemoMedication(
                "22222222-2222-2222-2222-222222222222",
                "어린이부루펜시럽(이부프로펜)", ProductType.PRESCRIPTION_DRUG,
                "198601920", "삼일제약(주)",
                "이부프로펜", "ibuprofen", "M051259", 2.0, "그램",
                "10mL", "20:00", "식후", true);
        if (!medications.containsKey(UUID.fromString("33333333-3333-3333-3333-333333333333"))) {
            putMedication("33333333-3333-3333-3333-333333333333", "오메가3 데모", ProductType.HEALTH_SUPPLEMENT,
                    "EPA 및 DHA", "omega3", "1캡슐", "13:00", "식후", false);
        }
    }

    private void removeLegacyDemoConsultation() {
        consultations.remove(LEGACY_DEMO_CONSULTATION);
        audioObjects.remove(LEGACY_DEMO_CONSULTATION);
        persistence.delete("consultation", LEGACY_DEMO_CONSULTATION.toString());
        persistence.delete("audio", LEGACY_DEMO_CONSULTATION.toString());
    }

    private void restore() {
        restoreUuid("medication", Medication.class, medications);
        restoreUuid("draft", PrescriptionDraft.class, drafts);
        restoreUuid("interaction-check", InteractionCheck.class, checks);
        restoreUuid("consultation", Consultation.class, consultations);
        restoreUuid("reminder", Reminder.class, reminders);
        restoreUuid("audio", ObjectStorage.StoredObject.class, audioObjects);
        persistence.load("idempotency", Accepted.class).forEach(idempotency::put);
        persistence.load("chat", ChatState.class).forEach((key, state) ->
                chatPrompts.put(UUID.fromString(key), Collections.synchronizedList(new ArrayList<>(state.prompts()))));
    }

    private <T> void restoreUuid(String type, Class<T> valueType, Map<UUID, T> destination) {
        persistence.load(type, valueType).forEach((key, value) -> destination.put(UUID.fromString(key), value));
    }

    private void putMedication(String id, String name, ProductType type, String ingredient,
                               String normalized, String dose, String time, String timing, boolean taken) {
        UUID uuid = UUID.fromString(id);
        Medication medication = new Medication(uuid, name, type, "DEMO-" + id.substring(0, 4),
                "데모 제조사", true, List.of(new Ingredient(ingredient, normalized, "MOCK", null, null)),
                dose, time, timing, taken, 0);
        medications.put(uuid, medication);
        persistence.put("medication", id, medication);
    }

    private void ensureOfficialDemoMedication(
            String id, String name, ProductType type, String productCode, String manufacturer,
            String ingredient, String normalized, String providerCode, Double amount, String unit,
            String dose, String time, String timing, boolean defaultTaken) {
        UUID uuid = UUID.fromString(id);
        Medication current = medications.get(uuid);
        if (current != null && current.productCode() != null && !current.productCode().startsWith("DEMO-")) return;
        Medication medication = new Medication(
                uuid, name, type, productCode, manufacturer, true,
                List.of(new Ingredient(ingredient, normalized, providerCode, amount, unit)),
                dose, time, timing,
                current != null ? current.taken() : defaultTaken,
                current != null ? current.version() : 0);
        medications.put(uuid, medication);
        persistence.put("medication", id, medication);
    }

    public HomeResponse home() {
        List<Medication> active = medications(true);
        int rx = (int) active.stream().filter(m -> m.productType() == ProductType.PRESCRIPTION_DRUG).count();
        int supp = (int) active.stream().filter(m -> m.productType() == ProductType.HEALTH_SUPPLEMENT).count();
        List<Medication> today = List.of(
                medication(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                medication(UUID.fromString("22222222-2222-2222-2222-222222222222")));
        return new HomeResponse("안녕하세요, 하늘님", "오늘 복용해야 할 약을 확인하세요.",
                new Counts(active.size(), rx, supp), today,
                "정보 제공용이며 복용 변경 전 의사·약사와 상담하세요.");
    }

    public List<Medication> medications(boolean activeOnly) {
        return medications.values().stream().filter(m -> !activeOnly || m.active())
                .sorted(Comparator.comparing(Medication::name)).toList();
    }

    public Medication medication(UUID id) {
        Medication value = medications.get(id);
        if (value == null) throw new NoSuchElementException("약을 찾을 수 없습니다.");
        return value;
    }

    public Medication createMedication(MedicationRequest request) {
        UUID id = UUID.randomUUID();
        Medication medication = new Medication(id, request.name(), request.productType(), request.productCode(),
                request.manufacturer(), request.active() == null || request.active(), request.ingredients(),
                request.dose(), request.time(), request.timing(), false, 0);
        medications.put(id, medication);
        persistence.put("medication", id.toString(), medication);
        return medication;
    }

    public Medication updateMedication(UUID id, MedicationRequest request) {
        Medication current = medication(id);
        if (request.version() != null && request.version() != current.version()) {
            throw new IllegalStateException("다른 기기에서 약 정보가 변경되었습니다.");
        }
        Medication next = new Medication(id, request.name(), request.productType(), request.productCode(),
                request.manufacturer(), request.active() == null ? current.active() : request.active(),
                request.ingredients(), request.dose(), request.time(), request.timing(), current.taken(),
                current.version() + 1);
        medications.put(id, next);
        persistence.put("medication", id.toString(), next);
        return next;
    }

    public Medication logDose(UUID id, DoseLogRequest request) {
        Medication current = medication(id);
        if (request.expectedVersion() != current.version()) throw new IllegalStateException("VERSION_CONFLICT");
        boolean taken = "TAKEN".equals(request.status());
        Medication next = new Medication(current.id(), current.name(), current.productType(), current.productCode(),
                current.manufacturer(), current.active(), current.ingredients(), current.dose(), current.time(),
                current.timing(), taken, current.version() + 1);
        medications.put(id, next);
        persistence.put("medication", id.toString(), next);
        return next;
    }

    public void deleteMedication(UUID id) {
        medication(id);
        medications.remove(id);
        persistence.delete("medication", id.toString());
    }

    public PrescriptionDraft createDraft() {
        UUID id = UUID.randomUUID();
        var candidates = List.of(
                new ProductCandidate("종합감기약 데모", "DEMO-COLD-01", "데모제약", 86, "의약품 제품 허가정보(mock)"),
                new ProductCandidate("종합감기약 데모 정", "DEMO-COLD-02", "데모제약", 81, "의약품 제품 허가정보(mock)"));
        var draft = new PrescriptionDraft(id, JobStatus.NEEDS_CONFIRMATION, "종합감기약 데모", "1정", 3, 3,
                "식후 30분", "데모제약", "DEMO-COLD-01",
                List.of(new Ingredient("아세트아미노펜", "acetaminophen", "MOCK-ING-01", 325d, "mg")),
                "감기 증상의 완화(데모)", 86, "식품의약품안전처 제품 허가정보 mock snapshot", candidates,
                List.of("OCR 결과는 자동 확정되지 않습니다.", "제품 후보가 둘 이상입니다. 확인해 주세요."));
        drafts.put(id, draft);
        persistence.put("draft", id.toString(), draft);
        return draft;
    }

    public PrescriptionDraft draft(UUID id) {
        PrescriptionDraft value = drafts.get(id);
        if (value == null) throw new NoSuchElementException("처방전 초안을 찾을 수 없습니다.");
        return value;
    }

    public PrescriptionDraft updateDraft(UUID id, DraftUpdate update) {
        PrescriptionDraft old = draft(id);
        var next = new PrescriptionDraft(id, JobStatus.NEEDS_CONFIRMATION, update.productName(), update.dose(),
                update.timesPerDay(), update.days(), update.timing(), update.manufacturer(), update.productCode(),
                update.ingredients(), old.efficacy(), 100, "사용자 확인", old.candidates(), List.of());
        drafts.put(id, next);
        persistence.put("draft", id.toString(), next);
        return next;
    }

    public Medication confirmDraft(UUID id) {
        PrescriptionDraft d = draft(id);
        var request = new MedicationRequest(d.productName(), ProductType.OTC_DRUG, d.productCode(), d.manufacturer(),
                d.ingredients(), d.dose(), "09:00", d.timing(), true, null);
        Medication medication = createMedication(request);
        drafts.put(id, new PrescriptionDraft(d.id(), JobStatus.SUCCEEDED, d.productName(), d.dose(), d.timesPerDay(),
                d.days(), d.timing(), d.manufacturer(), d.productCode(), d.ingredients(), d.efficacy(),
                d.matchConfidence(), d.source(), d.candidates(), d.warnings()));
        persistence.put("draft", id.toString(), drafts.get(id));
        return medication;
    }

    public synchronized Accepted createCheck(String key, InteractionRequest request) {
        String scopedKey = idempotencyKey("interaction", key);
        if (scopedKey != null && idempotency.containsKey(scopedKey)) return idempotency.get(scopedKey);
        Medication added = medication(request.newMedicationId());
        List<Medication> current = request.existingMedicationIds().stream().map(this::medication).toList();
        InteractionEngine.Analysis analysis = engine.analyze(added, current, (a, b) -> Optional.empty());
        UUID id = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        checks.put(id, new InteractionCheck(id, job, JobStatus.SUCCEEDED, analysis.results(), analysis.coverage(), false,
                "정보 제공용이며 복용 변경 전 의사·약사와 상담하세요."));
        persistence.put("interaction-check", id.toString(), checks.get(id));
        Accepted accepted = new Accepted(id, job, JobStatus.SUCCEEDED);
        if (scopedKey != null) saveIdempotency(scopedKey, accepted);
        return accepted;
    }

    public InteractionCheck check(UUID id) {
        InteractionCheck value = checks.get(id);
        if (value == null) throw new NoSuchElementException("분석 결과를 찾을 수 없습니다.");
        return value;
    }

    public InteractionCheck saveCheck(UUID id) {
        InteractionCheck c = check(id);
        InteractionCheck saved = new InteractionCheck(c.id(), c.jobId(), c.status(), c.results(), c.coverage(), true, c.disclaimer());
        checks.put(id, saved);
        persistence.put("interaction-check", id.toString(), saved);
        return saved;
    }

    public List<Consultation> consultations() {
        return consultations.values().stream()
                .sorted(Comparator.comparing(Consultation::consultedAt).reversed().thenComparing(Consultation::id))
                .toList();
    }
    public Consultation consultation(UUID id) {
        Consultation value = consultations.get(id);
        if (value == null) throw new NoSuchElementException("진료 기록을 찾을 수 없습니다.");
        return value;
    }

    public synchronized Accepted createConsultation(String idempotencyKey, String title, String hospital,
                                                    Instant at, long duration) {
        String scopedKey = idempotencyKey("consultation", idempotencyKey);
        if (scopedKey != null && idempotency.containsKey(scopedKey)) return idempotency.get(scopedKey);
        UUID id = UUID.randomUUID();
        consultations.put(id, new Consultation(id, title, hospital, at, duration, JobStatus.QUEUED, List.of(), null,
                null, null));
        persistence.put("consultation", id.toString(), consultations.get(id));
        Accepted accepted = new Accepted(id, UUID.randomUUID(), JobStatus.QUEUED);
        if (scopedKey != null) saveIdempotency(scopedKey, accepted);
        return accepted;
    }
    public Consultation updateConsultation(UUID id, JobStatus status, List<TranscriptSegment> transcript,
                                           ConsultationSummary summary) {
        Consultation current = consultation(id);
        Consultation updated = new Consultation(current.id(), current.title(), current.hospitalName(),
                current.consultedAt(), current.durationMs(), status, transcript, summary, null, null);
        consultations.put(id, updated);
        persistence.put("consultation", id.toString(), updated);
        return updated;
    }
    public Consultation failConsultation(UUID id, String failureCode, String failureMessage) {
        Consultation current = consultation(id);
        Consultation updated = new Consultation(current.id(), current.title(), current.hospitalName(),
                current.consultedAt(), current.durationMs(), JobStatus.FAILED, List.of(), null,
                failureCode, failureMessage);
        consultations.put(id, updated);
        persistence.put("consultation", id.toString(), updated);
        return updated;
    }
    public void deleteConsultation(UUID id) {
        consultation(id);
        consultations.remove(id);
        persistence.delete("consultation", id.toString());
    }

    public List<Reminder> reminders() { return reminders.values().stream().toList(); }
    public Reminder saveReminder(UUID id, ReminderRequest request) {
        Reminder value = new Reminder(id == null ? UUID.randomUUID() : id, request.medicationId(),
                request.localTime(), request.weekdays(), request.enabled());
        medication(value.medicationId());
        reminders.put(value.id(), value);
        persistence.put("reminder", value.id().toString(), value);
        return value;
    }
    public void deleteReminder(UUID id) {
        reminders.remove(id);
        persistence.delete("reminder", id.toString());
    }

    public ChatSession createChat() {
        UUID id = UUID.randomUUID();
        chatPrompts.put(id, Collections.synchronizedList(new ArrayList<>()));
        chatOfficialContexts.put(id, "");
        persistence.put("chat", id.toString(), new ChatState(List.of()));
        return new ChatSession(id, Instant.now());
    }
    public UUID addChatMessage(UUID sessionId, String message, String officialContext) {
        List<String> prompts = chatPrompts.get(sessionId);
        if (prompts == null) throw new NoSuchElementException("채팅 세션을 찾을 수 없습니다.");
        prompts.add(message);
        chatOfficialContexts.put(sessionId, officialContext == null ? "" : officialContext.strip());
        persistence.put("chat", sessionId.toString(), new ChatState(List.copyOf(prompts)));
        return UUID.randomUUID();
    }
    public String lastPrompt(UUID id) {
        List<String> prompts = chatPrompts.get(id);
        if (prompts == null || prompts.isEmpty()) throw new NoSuchElementException("질문을 찾을 수 없습니다.");
        return prompts.get(prompts.size() - 1);
    }
    public String lastOfficialContext(UUID id) {
        if (!chatPrompts.containsKey(id)) throw new NoSuchElementException("채팅 세션을 찾을 수 없습니다.");
        return chatOfficialContexts.getOrDefault(id, "");
    }

    public void saveAudio(UUID consultationId, ObjectStorage.StoredObject object) {
        audioObjects.put(consultationId, object);
        persistence.put("audio", consultationId.toString(), object);
    }

    public Optional<ObjectStorage.StoredObject> audio(UUID consultationId) {
        return Optional.ofNullable(audioObjects.get(consultationId));
    }

    public void deleteAudio(UUID consultationId) {
        audioObjects.remove(consultationId);
        persistence.delete("audio", consultationId.toString());
    }

    private void saveIdempotency(String key, Accepted accepted) {
        idempotency.put(key, accepted);
        persistence.put("idempotency", key, accepted);
    }

    private String idempotencyKey(String scope, String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (!normalized.matches("[a-zA-Z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("Idempotency-Key 형식이 올바르지 않습니다.");
        }
        return scope + "-" + normalized;
    }

    private record ChatState(List<String> prompts) { }
}
