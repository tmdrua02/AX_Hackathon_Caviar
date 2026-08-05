package com.haneul.medassist.service

import com.haneul.medassist.domain.medication.NormalizedDrugName
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

@Component
class DrugNameNormalizer {
    fun normalize(query: String): NormalizedDrugName {
        val original = query
        val unicodeNormalized = Normalizer.normalize(query, Normalizer.Form.NFKC)
        val normalizedQuery = normalizeUnits(unicodeNormalized)
            .replace(CONTROL_CHARACTERS, " ")
            .replace(MULTIPLE_WHITESPACE, " ")
            .trim()

        val parentheticalHints = PARENTHETICAL.findAll(normalizedQuery)
            .map { it.groupValues[1].trim() }
            .filter(String::isNotBlank)
            .toList()
        val manufacturerHint = parentheticalHints.firstOrNull(::looksLikeManufacturer)

        val strengthMatch = STRENGTH.find(normalizedQuery)
        val strengthAmount = strengthMatch?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toBigDecimalOrNull()
        val strengthUnit = strengthMatch?.groupValues?.get(2)?.lowercase(Locale.ROOT)

        var withoutDetails = normalizedQuery
            .replace(PARENTHETICAL, " ")
            .replace(STRENGTH, " ")
            .replace(MULTIPLE_WHITESPACE, " ")
            .trim()

        val dosageFormMatch = DOSAGE_FORM_AT_END.find(withoutDetails)
        val dosageForm = dosageFormMatch?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        if (dosageFormMatch != null) {
            withoutDetails = withoutDetails.removeRange(dosageFormMatch.range)
                .replace(MULTIPLE_WHITESPACE, " ")
                .trim()
        }

        val baseName = withoutDetails
            .replace(UNNECESSARY_SYMBOLS, " ")
            .replace(MULTIPLE_WHITESPACE, " ")
            .trim()

        return NormalizedDrugName(
            baseName = baseName,
            dosageForm = dosageForm,
            strengthAmount = strengthAmount,
            strengthUnit = strengthUnit,
            manufacturerHint = manufacturerHint,
            parentheticalHints = parentheticalHints,
            originalQuery = original,
            normalizedQuery = normalizedQuery,
            compactQuery = compact(normalizedQuery),
        )
    }

    fun compact(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(NON_SEARCH_CHARACTERS, "")

    private fun normalizeUnits(value: String): String {
        val wordsNormalized = value
            .replace(Regex("마이크로그램", RegexOption.IGNORE_CASE), "mcg")
            .replace(Regex("밀리그램", RegexOption.IGNORE_CASE), "mg")
            .replace(Regex("밀리리터", RegexOption.IGNORE_CASE), "ml")
            .replace(Regex("그램", RegexOption.IGNORE_CASE), "g")
            .replace(Regex("[μµ]g", RegexOption.IGNORE_CASE), "mcg")

        return STRENGTH.replace(wordsNormalized) { match ->
            "${match.groupValues[1].replace(',', '.')} ${match.groupValues[2].lowercase(Locale.ROOT)}"
        }
    }

    private fun looksLikeManufacturer(value: String): Boolean =
        MANUFACTURER_MARKERS.containsMatchIn(value)

    companion object {
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]")
        private val MULTIPLE_WHITESPACE = Regex("\\s+")
        private val PARENTHETICAL = Regex("\\(([^()]*)\\)")
        private val STRENGTH = Regex(
            "(\\d+(?:[.,]\\d+)?)\\s*(mcg|mg|ml|g)(?=$|[^a-z])",
            RegexOption.IGNORE_CASE,
        )
        private val DOSAGE_FORM_AT_END = Regex(
            "(?:\\s*)(연질캡슐|캡슐|시럽|주사|크림|연고|패치|과립|정|액|주|산)\\s*$",
            RegexOption.IGNORE_CASE,
        )
        private val MANUFACTURER_MARKERS = Regex(
            "(주식회사|㈜|제약|약품|pharm|company|co\\.?|ltd\\.?)",
            RegexOption.IGNORE_CASE,
        )
        private val UNNECESSARY_SYMBOLS = Regex("[^가-힣a-zA-Z0-9\\s-]")
        private val NON_SEARCH_CHARACTERS = Regex("[^가-힣a-z0-9]")
    }
}
