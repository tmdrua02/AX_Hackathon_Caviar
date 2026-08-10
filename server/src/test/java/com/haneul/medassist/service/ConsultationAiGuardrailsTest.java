package com.haneul.medassist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultationAiGuardrailsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsSubSecondAudioRegardlessOfTranscriptLength() throws Exception {
        var response = mapper.readTree("""
                {"text":"안녕하세요. 일주일 전부터 기침이 계속되고 열이 납니다.","logprobs":[]}
                """);

        assertEquals("TRANSCRIPTION_IMPLAUSIBLE",
                ConsultationAiGuardrails.transcriptionFailure(response, 576).orElseThrow());
    }

    @Test
    void doesNotRejectLongTranscriptBasedOnCharacterCount() throws Exception {
        String longText = "복통과 설사가 반복되었습니다. ".repeat(80);
        var response = mapper.createObjectNode();
        response.put("text", longText);
        response.putArray("logprobs")
                .addObject().put("token", "복통").put("logprob", -0.05);

        assertTrue(ConsultationAiGuardrails.transcriptionFailure(response, 60_000).isEmpty());
    }

    @Test
    void rejectsLowConfidenceTranscription() throws Exception {
        var response = mapper.readTree("""
                {"text":"배가 아프고 설사를 했어요.","logprobs":[
                  {"token":"배가","logprob":-1.8},{"token":" 아프고","logprob":-1.4},
                  {"token":" 설사를","logprob":-2.2},{"token":" 했어요","logprob":-1.7}
                ]}
                """);

        assertEquals("TRANSCRIPTION_LOW_CONFIDENCE",
                ConsultationAiGuardrails.transcriptionFailure(response, 10_000).orElseThrow());
    }

    @Test
    void acceptsPlausibleHighConfidenceTranscription() throws Exception {
        var response = mapper.readTree("""
                {"text":"의사: 어디가 불편하세요? 환자: 어제부터 배가 아프고 설사를 했어요.","logprobs":[
                  {"token":"어디가","logprob":-0.08},{"token":" 불편하세요","logprob":-0.12},
                  {"token":" 배가","logprob":-0.05},{"token":" 아프고","logprob":-0.09}
                ]}
                """);

        assertTrue(ConsultationAiGuardrails.transcriptionFailure(response, 12_000).isEmpty());
    }

    @Test
    void dialogueMustContainOnlyOriginalTranscriptWordsInOrder() throws Exception {
        String transcript = "의사: 어디가 불편하세요? 환자: 어제부터 배가 아프고 설사를 했어요.";
        var grounded = mapper.readTree("""
                [{"speaker":"의사","text":"어디가 불편하세요?"},
                 {"speaker":"환자","text":"어제부터 배가 아프고 설사를 했어요."}]
                """);
        var invented = mapper.readTree("""
                [{"speaker":"의사","text":"어디가 불편하세요?"},
                 {"speaker":"환자","text":"어제부터 기침이 나고 열이 났어요."}]
                """);

        assertTrue(ConsultationAiGuardrails.dialogueMatchesTranscript(transcript, grounded));
        assertFalse(ConsultationAiGuardrails.dialogueMatchesTranscript(transcript, invented));
    }
}
