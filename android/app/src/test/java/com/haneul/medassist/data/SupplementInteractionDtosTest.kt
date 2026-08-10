package com.haneul.medassist.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupplementInteractionDtosTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun requestEncodesOfficialIdentifiers() {
        val encoded = json.encodeToString(
            SupplementInteractionCheckRequest("TEST_ITEM_SEQ", "TEST_STTEMNT_NO"),
        )

        assertEquals(
            "{\"medicationProductCode\":\"TEST_ITEM_SEQ\",\"supplementStatementNo\":\"TEST_STTEMNT_NO\"}",
            encoded,
        )
    }

    @Test
    fun responseDecodesUnknownAsSuccessfulAnalysisAndPreservesNullableExplanationMetadata() {
        val response = json.decodeFromString<SupplementInteractionCheckResponse>(
            supplementInteractionResponseJson(),
        )

        assertEquals(SupplementInteractionSeverity.UNKNOWN, response.severityValue)
        assertEquals(SupplementInteractionExplanationStatus.UNAVAILABLE, response.explanation.statusValue)
        assertNull(response.explanation.provider)
        assertNull(response.explanation.model)
        assertEquals("TEST_ITEM_SEQ", response.medication?.productCode)
        assertEquals("TEST_STTEMNT_NO", response.supplement?.statementNo)
        assertEquals(false, response.coverage.complete)
    }

    @Test
    fun allKnownExplanationStatusesDecode() {
        SupplementInteractionExplanationStatus.entries.forEach { status ->
            val response = json.decodeFromString<SupplementInteractionCheckResponse>(
                supplementInteractionResponseJson(explanationStatus = status.name),
            )
            assertEquals(status, response.explanation.statusValue)
            assertEquals("TEST disclaimer", response.disclaimer)
            assertEquals("TEST summary", response.explanation.summary)
        }
    }

    @Test
    fun futureWireEnumsDoNotBreakWholeResponse() {
        val response = json.decodeFromString<SupplementInteractionCheckResponse>(
            supplementInteractionResponseJson(severity = "FUTURE_SEVERITY", explanationStatus = "FUTURE_STATUS"),
        )

        assertEquals("FUTURE_SEVERITY", response.severity)
        assertEquals(SupplementInteractionSeverity.UNKNOWN, response.severityValue)
        assertEquals(SupplementInteractionExplanationStatus.UNAVAILABLE, response.explanation.statusValue)
    }

    @Test
    fun noVerifiedRuleFoundRemainsAParsedSuccessValue() {
        val response = json.decodeFromString<SupplementInteractionCheckResponse>(
            supplementInteractionResponseJson(
                severity = "NO_VERIFIED_RULE_FOUND",
                explanationStatus = "FALLBACK",
                failedSteps = "[]",
            ),
        )

        assertEquals(SupplementInteractionSeverity.NO_VERIFIED_RULE_FOUND, response.severityValue)
        assertEquals(SupplementInteractionExplanationStatus.FALLBACK, response.explanation.statusValue)
    }
}
