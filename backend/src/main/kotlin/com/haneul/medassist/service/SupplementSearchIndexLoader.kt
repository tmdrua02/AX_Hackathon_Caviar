package com.haneul.medassist.service

import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import org.springframework.stereotype.Component

fun interface SupplementSearchIndexLoader {
    fun load(): List<SupplementSearchCandidate>
}

@Component
class InMemorySupplementSearchIndexLoader(
    private val candidates: List<SupplementSearchCandidate> = emptyList(),
) : SupplementSearchIndexLoader {
    override fun load(): List<SupplementSearchCandidate> = candidates.toList()
}
