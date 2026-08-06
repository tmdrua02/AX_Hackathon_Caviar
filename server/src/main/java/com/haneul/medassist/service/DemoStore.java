package com.haneul.medassist.service;

import com.haneul.medassist.api.ApiModels.*;
import com.haneul.medassist.domain.InteractionEngine;
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
    private final Map<UUID, Medication> medications = new ConcurrentHashMap<>();
    private final Map<UUID, PrescriptionDraft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, InteractionCheck> checks = new ConcurrentHashMap<>();
    private final Map<UUID, Consultation> consultations = new ConcurrentHashMap<>();
    private final Map<UUID, Reminder> reminders = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> chatPrompts = new ConcurrentHashMap<>();
    private final Map<String, Accepted> idempotency = new ConcurrentHashMap<>();
    private final InteractionEngine engine = new InteractionEngine();

    @PostConstruct
    void seed() {
        putMedication("11111111-1111-1111-1111-111111111111", "타이레놀", ProductType.OTC_DRUG,
                "아세트아미노펜", "acetaminophen", "1정", "09:00", "식후", false);
        putMedication("22222222-2222-2222-2222-222222222222", "해열 시럽 A", ProductType.PRESCRIPTION_DRUG,
                "이부프로펜", "ibuprofen", "10mL", "20:00", "식후", true);
        putMedication("33333333-3333-3333-3333-333333333333", "오메가3 데모", ProductType.HEALTH_SUPPLEMENT,
                "EPA 및 DHA", "omega3", "1캡슐", "13:00", "식후", false);
        seedConsultation();
    }

    private void putMedication(String id, String name, ProductType type, String ingredient,
                               String normalized, String dose, String time, String timing, boolean taken) {
        UUID uuid = UUID.fromString(id);
        medications.put(uuid, new Medication(uuid, name, type, "DEMO-" + id.substring(0, 4),
                "데모 제조사", true, List.of(new Ingredient(ingredient, normalized, "MOCK", null, null)),
                dose, time, timing, taken, 0));
    }

    private void seedConsultation() {
        UUID cId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        var segments = List.of(
                new TranscriptSegment(s1, "의사", 0, 8200, "어디가 가장 불편해서 오셨어요?"),
                new TranscriptSegment(s2, "환자", 8400, 18300, "어제부터 목이 따갑고 미열이 있었어요."),
                new TranscriptSegment(UUID.randomUUID(), "의사", 19000, 31000, "물을 충분히 드시고 증상이 심해지면 다시 내원하세요."));
        var summary = new ConsultationSummary("목 불편감과 미열에 관해 상담한 데모 진료 기록입니다.",
                List.of(new SummaryItem("목 따가움과 미열", List.of(s2))), List.of(), List.of(),
                List.of(new SummaryItem("증상이 심해지면 재내원", List.of(segments.get(2).id()))),
                List.of(new SummaryItem("화자 구분은 AI 추정이므로 원음 확인 필요", List.of(s1, s2))));
        consultations.put(cId, new Consultation(cId, "감기 증상 진료", "하늘내과(데모)",
                Instant.parse("2026-08-03T01:30:00Z"), 31_000, JobStatus.SUCCEEDED, segments, summary, null, null));
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
        return next;
    }

    public void deleteMedication(UUID id) { medication(id); medications.remove(id); }

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
        return medication;
    }

    public Accepted createCheck(String key, InteractionRequest request) {
        if (key != null && idempotency.containsKey(key)) return idempotency.get(key);
        Medication added = medication(request.newMedicationId());
        List<Medication> current = request.existingMedicationIds().stream().map(this::medication).toList();
        InteractionEngine.Analysis analysis = engine.analyze(added, current, (a, b) -> Optional.empty());
        UUID id = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        checks.put(id, new InteractionCheck(id, job, JobStatus.SUCCEEDED, analysis.results(), analysis.coverage(), false,
                "정보 제공용이며 복용 변경 전 의사·약사와 상담하세요."));
        Accepted accepted = new Accepted(id, job, JobStatus.SUCCEEDED);
        if (key != null) idempotency.put(key, accepted);
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
        return saved;
    }

    public List<Consultation> consultations() { return consultations.values().stream().toList(); }
    public Consultation consultation(UUID id) {
        Consultation value = consultations.get(id);
        if (value == null) throw new NoSuchElementException("진료 기록을 찾을 수 없습니다.");
        return value;
    }

    public Accepted createConsultation(String title, String hospital, Instant at, long duration) {
        UUID id = UUID.randomUUID();
        consultations.put(id, new Consultation(id, title, hospital, at, duration, JobStatus.QUEUED, List.of(), null,
                null, null));
        return new Accepted(id, UUID.randomUUID(), JobStatus.QUEUED);
    }
    public Consultation updateConsultation(UUID id, JobStatus status, List<TranscriptSegment> transcript,
                                           ConsultationSummary summary) {
        Consultation current = consultation(id);
        Consultation updated = new Consultation(current.id(), current.title(), current.hospitalName(),
                current.consultedAt(), current.durationMs(), status, transcript, summary, null, null);
        consultations.put(id, updated);
        return updated;
    }
    public Consultation failConsultation(UUID id, String failureCode, String failureMessage) {
        Consultation current = consultation(id);
        Consultation updated = new Consultation(current.id(), current.title(), current.hospitalName(),
                current.consultedAt(), current.durationMs(), JobStatus.FAILED, List.of(), null,
                failureCode, failureMessage);
        consultations.put(id, updated);
        return updated;
    }
    public void deleteConsultation(UUID id) { consultation(id); consultations.remove(id); }

    public List<Reminder> reminders() { return reminders.values().stream().toList(); }
    public Reminder saveReminder(UUID id, ReminderRequest request) {
        Reminder value = new Reminder(id == null ? UUID.randomUUID() : id, request.medicationId(),
                request.localTime(), request.weekdays(), request.enabled());
        medication(value.medicationId());
        reminders.put(value.id(), value);
        return value;
    }
    public void deleteReminder(UUID id) { reminders.remove(id); }

    public ChatSession createChat() {
        UUID id = UUID.randomUUID();
        chatPrompts.put(id, Collections.synchronizedList(new ArrayList<>()));
        return new ChatSession(id, Instant.now());
    }
    public UUID addChatMessage(UUID sessionId, String message) {
        List<String> prompts = chatPrompts.get(sessionId);
        if (prompts == null) throw new NoSuchElementException("채팅 세션을 찾을 수 없습니다.");
        prompts.add(message);
        return UUID.randomUUID();
    }
    public String lastPrompt(UUID id) {
        List<String> prompts = chatPrompts.get(id);
        if (prompts == null || prompts.isEmpty()) throw new NoSuchElementException("질문을 찾을 수 없습니다.");
        return prompts.get(prompts.size() - 1);
    }
}
