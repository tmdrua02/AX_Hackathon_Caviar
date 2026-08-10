package com.haneul.medassist.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Optional;

/** Rejects implausible ASR output and summaries that add words not present in the transcript. */
final class ConsultationAiGuardrails {
    private static final long MIN_DURATION_MS = 3_000L;
    private static final double MIN_AVERAGE_LOG_PROBABILITY = -1.0;
    private static final double VERY_LOW_LOG_PROBABILITY = -2.0;
    private static final double MAX_VERY_LOW_CONFIDENCE_RATIO = 0.35;

    private ConsultationAiGuardrails() { }

    static Optional<String> transcriptionFailure(JsonNode transcription, long durationMs) {
        String normalized = normalize(transcription.path("text").asText(""));
        if (normalized.isBlank()) return Optional.of("TRANSCRIPTION_EMPTY");
        if (durationMs < MIN_DURATION_MS) return Optional.of("TRANSCRIPTION_IMPLAUSIBLE");

        JsonNode logprobs = transcription.path("logprobs");
        if (logprobs.isArray() && !logprobs.isEmpty()) {
            double sum = 0.0;
            int count = 0;
            int veryLowConfidenceCount = 0;
            for (JsonNode token : logprobs) {
                JsonNode value = token.path("logprob");
                if (!value.isNumber()) continue;
                double logprob = value.asDouble();
                sum += logprob;
                count++;
                if (logprob < VERY_LOW_LOG_PROBABILITY) veryLowConfidenceCount++;
            }
            if (count > 0) {
                double average = sum / count;
                double veryLowRatio = veryLowConfidenceCount / (double) count;
                if (average < MIN_AVERAGE_LOG_PROBABILITY || veryLowRatio > MAX_VERY_LOW_CONFIDENCE_RATIO) {
                    return Optional.of("TRANSCRIPTION_LOW_CONFIDENCE");
                }
            }
        }
        return Optional.empty();
    }

    static boolean dialogueMatchesTranscript(String transcript, JsonNode dialogue) {
        if (!dialogue.isArray() || dialogue.isEmpty()) return false;
        StringBuilder combined = new StringBuilder();
        for (JsonNode row : dialogue) combined.append(row.path("text").asText(""));
        return normalize(stripSpeakerLabels(transcript)).equals(normalize(combined.toString()));
    }

    private static String stripSpeakerLabels(String value) {
        return value.replaceAll("(?m)(의사|환자|화자\\s*[A-Za-z가-힣0-9]+)\\s*[:：]", "");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣]", "");
    }
}
