package com.haneul.medassist.service;

import com.haneul.medassist.integration.OpenAiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSafetyServiceTest {
    @Test
    void officialInteractionContextIsUsedByLocalSafetyFallback() {
        var provider = new StaticListableBeanFactory().getBeanProvider(OpenAiGateway.class);
        var contextProvider = new StaticListableBeanFactory().getBeanProvider(OfficialMedicationContextService.class);
        var service = new ChatSafetyService(provider, contextProvider);
        var answer = service.answer(
                "두 약을 같이 먹어도 돼?",
                "조합: 새 약 / 기존 약\n판정: PROHIBITED\n공식 근거: DUR-1");

        assertTrue(answer.contains("PROHIBITED"));
        assertTrue(answer.contains("DUR-1"));
        assertTrue(answer.contains("의사·약사"));
    }
}
