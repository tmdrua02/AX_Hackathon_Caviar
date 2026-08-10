package com.haneul.medassist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionChatContextFormatterTest {
    @Test
    fun `official result is formatted for chatbot context`() {
        val medication = Medication("new", "새 약", ProductType.OTC_DRUG, productCode = "NEW-1")
        val existing = Medication("old", "기존 약", ProductType.PRESCRIPTION_DRUG, productCode = "OLD-1")
        val check = InteractionCheck(
            id = "check",
            jobId = "job",
            status = "COMPLETED",
            results = listOf(
                InteractionResult(
                    id = "result",
                    newMedication = medication,
                    existingMedication = existing,
                    severity = Severity.PROHIBITED,
                    title = "금기",
                    easyExplanation = "공식 DUR 병용금기 근거가 확인되었습니다.",
                    evidence = listOf(
                        Evidence(
                            ingredientA = "성분 A",
                            ingredientB = "성분 B",
                            evidenceType = "PROHIBITED",
                            sourceName = "식품의약품안전처",
                            sourceUrl = "https://example.test",
                            sourceRecordId = "DUR-1",
                            retrievedAt = "2026-08-10T00:00:00Z",
                            sourceType = "PUBLIC_DATA",
                        ),
                    ),
                ),
            ),
            coverage = Coverage(2, 1, 0, false),
            saved = false,
            disclaimer = "전문가와 상담하세요.",
        )

        val context = InteractionChatContextFormatter.format(check).orEmpty()

        assertTrue(context.contains("판정: PROHIBITED"))
        assertTrue(context.contains("DUR-1"))
        assertTrue(context.contains("식품의약품안전처"))
    }

    @Test
    fun `missing result does not invent context`() {
        assertEquals(null, InteractionChatContextFormatter.format(null))
    }
}
