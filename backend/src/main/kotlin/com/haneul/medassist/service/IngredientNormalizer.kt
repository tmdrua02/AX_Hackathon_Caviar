package com.haneul.medassist.service

import com.haneul.medassist.domain.medication.Ingredient
import com.haneul.medassist.domain.medication.SourceMetadata
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

@Component
class IngredientNormalizer {
    fun normalize(
        providerCode: String?,
        displayName: String,
        koreanName: String?,
        englishName: String?,
        amount: BigDecimal?,
        unit: String?,
        source: SourceMetadata,
    ): Ingredient {
        val comparisonName = englishName?.takeIf(String::isNotBlank)
            ?: koreanName?.takeIf(String::isNotBlank)
            ?: displayName
        val normalizedName = normalizeName(comparisonName)

        return Ingredient(
            providerCode = providerCode?.trim()?.takeIf(String::isNotBlank),
            displayName = displayName.trim(),
            koreanName = koreanName?.trim()?.takeIf(String::isNotBlank),
            englishName = englishName?.trim()?.takeIf(String::isNotBlank),
            normalizedName = normalizedName,
            amount = amount,
            unit = normalizeUnit(unit),
            saltForm = SALT_MARKER.find(normalizedName)?.value,
            hydrateForm = HYDRATE_MARKER.find(normalizedName)?.value,
            source = source,
        )
    }

    fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    fun normalizeUnit(value: String?): String? {
        val normalized = value?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace("μg", "mcg")
            ?.replace("µg", "mcg")
            ?.takeIf(String::isNotBlank)
        return normalized
    }

    companion object {
        // 염·수화물 표현은 비교에서 제거하지 않고 메타데이터로만 표시한다.
        private val SALT_MARKER = Regex("(hydrochloride|sodium|calcium|potassium|염산염|나트륨|칼슘|칼륨)")
        private val HYDRATE_MARKER = Regex("(hydrate|monohydrate|dihydrate|trihydrate|수화물|일수화물|이수화물|삼수화물)")
    }
}
