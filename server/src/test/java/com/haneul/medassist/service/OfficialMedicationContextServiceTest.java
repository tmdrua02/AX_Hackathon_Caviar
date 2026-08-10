package com.haneul.medassist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haneul.medassist.integration.OpenAiGateway;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfficialMedicationContextServiceTest {
    private HttpServer backend;

    @AfterEach
    void stopBackend() {
        if (backend != null) backend.stop(0);
    }

    @Test
    void ambiguousMedicationCategoryRequestsClarificationWithoutBackendLookup() throws Exception {
        OpenAiGateway openAi = mock(OpenAiGateway.class);
        when(openAi.extractMedicationQueries("타이레놀과 해열제를 같이 먹어도 돼?"))
                .thenReturn(new OpenAiGateway.MedicationQueryExtraction(
                        true, List.of("타이레놀"), List.of("해열제")));
        var service = new OfficialMedicationContextService(openAi, new ObjectMapper(), "http://127.0.0.1:1");

        var resolution = service.resolve("타이레놀과 해열제를 같이 먹어도 돼?");

        assertTrue(resolution.applicable());
        assertNull(resolution.officialContext());
        assertTrue(resolution.directAnswer().contains("해열제"));
        assertTrue(resolution.directAnswer().contains("정확한 의약품 제품명"));
    }

    @Test
    void exactOfficialProductsCreateIngredientAndDurContext() throws Exception {
        startBackend();
        OpenAiGateway openAi = mock(OpenAiGateway.class);
        String prompt = "타이레놀정500밀리그람(아세트아미노펜)과 부루펜정200밀리그램(이부프로펜)을 함께 먹어도 돼?";
        when(openAi.extractMedicationQueries(prompt)).thenReturn(new OpenAiGateway.MedicationQueryExtraction(
                true,
                List.of("타이레놀정500밀리그람", "아세트아미노펜", "부루펜정200밀리그램", "이부프로펜"),
                List.of()));
        var service = new OfficialMedicationContextService(
                openAi, new ObjectMapper(), "http://127.0.0.1:" + backend.getAddress().getPort());

        var resolution = service.resolve(prompt);

        assertTrue(resolution.applicable());
        assertNull(resolution.directAnswer());
        assertNotNull(resolution.officialContext());
        assertTrue(resolution.officialContext().contains("아세트아미노펜"));
        assertTrue(resolution.officialContext().contains("이부프로펜"));
        assertTrue(resolution.officialContext().contains("식품의약품안전처 DUR"));
        assertTrue(resolution.officialContext().contains("성분쌍 1/1"));
    }

    private void startBackend() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/api/v1/drug-products/search", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request.contains("타이레놀")) {
                respond(exchange, """
                        {"candidates":[{"productCode":"TYLENOL-500","productName":"타이레놀정500밀리그람(아세트아미노펜)","manufacturer":"켄뷰","ingredients":[{"displayName":"아세트아미노펜"}],"matchConfidence":100,"ingredientLookupStatus":"RESOLVED"}]}
                        """);
            } else {
                respond(exchange, """
                        {"candidates":[{"productCode":"BRUFEN-200","productName":"부루펜정200밀리그램(이부프로펜)","manufacturer":"삼일제약","ingredients":[{"displayName":"이부프로펜"}],"matchConfidence":100,"ingredientLookupStatus":"RESOLVED"}]}
                        """);
            }
        });
        backend.createContext("/api/v1/drug-interaction-checks", exchange -> respond(exchange, """
                {
                  "processingStatus":"COMPLETED",
                  "results":[{
                    "newMedication":{"productName":"타이레놀정500밀리그람"},
                    "existingMedication":{"productName":"부루펜정200밀리그램"},
                    "severity":"CAUTION",
                    "summary":"공식 근거 범위에서 주의가 필요합니다.",
                    "coverage":{"completedPairs":1,"totalPairs":1,"complete":true},
                    "evidence":[{"ingredientA":"아세트아미노펜","ingredientB":"이부프로펜","evidenceType":"CAUTION","sourceName":"식품의약품안전처 DUR","sourceRecordId":"DUR-1"}]
                  }],
                  "disclaimer":"복용 전 의사 또는 약사와 상담하세요."
                }
                """));
        backend.start();
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
