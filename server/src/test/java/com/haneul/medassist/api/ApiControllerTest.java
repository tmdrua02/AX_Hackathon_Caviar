package com.haneul.medassist.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void homeReturnsSeedDataAndSafetyDisclaimer() throws Exception {
        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting").value("안녕하세요, 하늘님"))
                .andExpect(jsonPath("$.todayMedications.length()").value(2))
                .andExpect(jsonPath("$.todayMedications[0].name").value("타이레놀정500밀리그람(아세트아미노펜)"))
                .andExpect(jsonPath("$.todayMedications[0].productCode").value("202106092"))
                .andExpect(jsonPath("$.todayMedications[0].ingredients[0].providerCode").value("M040353"))
                .andExpect(jsonPath("$.todayMedications[1].name").value("어린이부루펜시럽(이부프로펜)"))
                .andExpect(jsonPath("$.todayMedications[1].productCode").value("198601920"))
                .andExpect(jsonPath("$.todayMedications[1].ingredients[0].providerCode").value("M051259"))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("의사·약사")));
    }

    @Test
    void otherDemoUserCannotReadResources() throws Exception {
        mvc.perform(get("/api/v1/home").header("X-Demo-User-Id", "00000000-0000-0000-0000-000000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void prescriptionRequiresBothValidImages() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.txt", "text/plain", "not-image".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/prescription-drafts").file(front).file(back))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void validTwoSidedUploadNeedsConfirmation() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});
        MockMultipartFile back = new MockMultipartFile("backImage", "back.jpg", "image/jpeg", new byte[]{4, 5, 6});
        mvc.perform(multipart("/api/v1/prescription-drafts").file(front).file(back)
                        .param("clientOcrText", "종합감기약"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("NEEDS_CONFIRMATION"))
                .andExpect(jsonPath("$.candidates.length()").value(2));
    }
}
