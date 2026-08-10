package com.haneul.medassist.service

import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.Locale

@Component
class SupplementNameNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}
