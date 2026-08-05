package com.haneul.medassist.domain.medication

import java.math.BigDecimal

data class NormalizedDrugName(
    val baseName: String,
    val dosageForm: String?,
    val strengthAmount: BigDecimal?,
    val strengthUnit: String?,
    val manufacturerHint: String?,
    val parentheticalHints: List<String>,
    val originalQuery: String,
    val normalizedQuery: String,
    val compactQuery: String,
)
