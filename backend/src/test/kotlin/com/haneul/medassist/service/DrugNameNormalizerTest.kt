package com.haneul.medassist.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DrugNameNormalizerTest {
    private val normalizer = DrugNameNormalizer()

    @Test
    fun `normalizes unicode whitespace dosage form and strength`() {
        val result = normalizer.normalize("  타이레놀  정  500㎎  ")

        assertEquals("타이레놀 정 500 mg", result.normalizedQuery)
        assertEquals("타이레놀", result.baseName)
        assertEquals("정", result.dosageForm)
        assertEquals("500".toBigDecimal(), result.strengthAmount)
        assertEquals("mg", result.strengthUnit)
    }

    @Test
    fun `parses compact korean strength notation`() {
        val result = normalizer.normalize("타이레놀정500밀리그램")

        assertEquals("타이레놀", result.baseName)
        assertEquals("정", result.dosageForm)
        assertEquals("500".toBigDecimal(), result.strengthAmount)
        assertEquals("mg", result.strengthUnit)
    }

    @Test
    fun `separates verified-looking manufacturer parenthetical`() {
        val result = normalizer.normalize("타이레놀정 500mg (한울제약 주식회사)")

        assertEquals("한울제약 주식회사", result.manufacturerHint)
        assertEquals(listOf("한울제약 주식회사"), result.parentheticalHints)
    }

    @Test
    fun `does not assume an ingredient parenthetical is a manufacturer`() {
        val result = normalizer.normalize("제품정(아세트아미노펜)")

        assertNull(result.manufacturerHint)
        assertEquals(listOf("아세트아미노펜"), result.parentheticalHints)
    }
}
