package com.haneul.medassist.service

import com.haneul.medassist.domain.medication.SourceMetadata
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IngredientNormalizerTest {
    private val normalizer = IngredientNormalizer()
    private val source = SourceMetadata("official", "record", Instant.EPOCH, "reference")

    @Test
    fun `preserves salt and hydrate information`() {
        val ingredient = normalizer.normalize(
            providerCode = "CODE-1",
            displayName = "아토르바스타틴칼슘삼수화물",
            koreanName = "아토르바스타틴칼슘삼수화물",
            englishName = "Atorvastatin Calcium Trihydrate",
            amount = "10.000".toBigDecimal(),
            unit = "㎎",
            source = source,
        )

        assertEquals("atorvastatin calcium trihydrate", ingredient.normalizedName)
        assertEquals("mg", ingredient.unit)
        assertNotNull(ingredient.saltForm)
        assertNotNull(ingredient.hydrateForm)
    }
}
