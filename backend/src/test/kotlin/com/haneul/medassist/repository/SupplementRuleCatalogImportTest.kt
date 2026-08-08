package com.haneul.medassist.repository

import com.haneul.medassist.MedassistBackendApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(
    classes = [MedassistBackendApplication::class],
    properties = [
        "medassist.supplement-interaction-rules.resource=classpath:supplement-interaction-rules-valid.json",
        "medassist.supplement-interaction-rules.require-verified-manifest=false",
    ],
)
class SupplementRuleCatalogImportTest {
    @Autowired
    private lateinit var catalog: JsonSupplementRuleCatalog

    @Test
    fun `validated JSON fixture loads through production startup path`() {
        val mapping = catalog.findVerifiedByStatementNo("TEST-STATEMENT-1", Instant.now()).single()
        val ingredient = catalog.findVerifiedById(mapping.supplementIngredientCanonicalId)
        val rules = catalog.findVerified("TEST-DRUG-CODE-1", mapping.supplementIngredientCanonicalId, Instant.now())

        assertNotNull(ingredient)
        assertEquals("TEST-RULE-1", rules.single().id)
        assertEquals("TEST-SOURCE-1", catalog.findVerifiedByIds(setOf("TEST-SOURCE-1")).single().id)
    }
}
