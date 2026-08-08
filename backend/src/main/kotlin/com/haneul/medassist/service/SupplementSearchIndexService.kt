package com.haneul.medassist.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.supplement.SupplementSearchCandidate
import com.haneul.medassist.domain.supplement.SupplementSearchMatch
import com.haneul.medassist.domain.supplement.SupplementSearchMatchType
import com.haneul.medassist.exception.ApiErrorCode
import com.haneul.medassist.exception.MedAssistException
import org.springframework.stereotype.Service

@Service
class SupplementSearchIndexService(
    loader: SupplementSearchIndexLoader,
    private val normalizer: SupplementNameNormalizer,
    cacheProperties: PublicDataCacheProperties,
) {
    private val candidates = loader.load()
        .filter { it.sttemntNo.isNotBlank() && it.productName.isNotBlank() }
        .distinctBy { it.sttemntNo }

    private val positiveCache: Cache<String, List<SupplementSearchMatch>> = Caffeine.newBuilder()
        .maximumSize(cacheProperties.maximumSize)
        .expireAfterWrite(cacheProperties.positiveSearchTtl)
        .build()

    private val negativeCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(cacheProperties.maximumSize)
        .expireAfterWrite(cacheProperties.negativeSearchTtl)
        .build()

    fun normalize(value: String): String = normalizer.normalize(value)

    fun search(query: String): List<SupplementSearchMatch> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) {
            throw MedAssistException(ApiErrorCode.VALIDATION_FAILED, "검색 가능한 제품명을 입력해 주세요.")
        }

        positiveCache.getIfPresent(normalizedQuery)?.let { return it }
        if (negativeCache.getIfPresent(normalizedQuery) == true) return emptyList()

        val matches = candidates.mapNotNull { candidate -> assess(normalizedQuery, candidate) }
            .sortedWith(
                compareByDescending<SupplementSearchMatch> { it.score }
                    .thenBy { it.candidate.productName }
                    .thenBy { it.candidate.sttemntNo },
            )

        if (matches.isEmpty()) {
            negativeCache.put(normalizedQuery, true)
        } else {
            positiveCache.put(normalizedQuery, matches)
        }
        return matches
    }

    private fun assess(
        normalizedQuery: String,
        candidate: SupplementSearchCandidate,
    ): SupplementSearchMatch? {
        val names = buildSet {
            add(normalize(candidate.productName))
            add(normalize(candidate.normalizedName))
            candidate.aliases.mapTo(this) { normalize(it) }
        }.filter(String::isNotBlank)

        val type = when {
            names.any { it == normalizedQuery } -> SupplementSearchMatchType.EXACT
            names.any { it.startsWith(normalizedQuery) } -> SupplementSearchMatchType.PREFIX
            names.any { it.contains(normalizedQuery) } -> SupplementSearchMatchType.CONTAINS
            else -> return null
        }
        return SupplementSearchMatch(
            candidate = candidate,
            score = SCORE_BY_TYPE.getValue(type),
            matchType = type,
        )
    }

    companion object {
        private val SCORE_BY_TYPE = mapOf(
            SupplementSearchMatchType.EXACT to 100,
            SupplementSearchMatchType.PREFIX to 80,
            SupplementSearchMatchType.CONTAINS to 60,
        )
    }
}
