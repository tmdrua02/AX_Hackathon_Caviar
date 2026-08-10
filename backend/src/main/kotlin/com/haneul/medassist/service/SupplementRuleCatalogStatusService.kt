package com.haneul.medassist.service

import com.haneul.medassist.domain.evidence.SupplementRuleCatalogAuditMetadata
import com.haneul.medassist.repository.SupplementRuleCatalogMetadataProvider
import org.springframework.stereotype.Service

@Service
class SupplementRuleCatalogStatusService(
    private val metadataProvider: SupplementRuleCatalogMetadataProvider,
) {
    fun status(): SupplementRuleCatalogAuditMetadata = metadataProvider.metadata()
}
