package com.haneul.medassist.controller

import com.haneul.medassist.client.drug.DrugProductApiClient
import com.haneul.medassist.client.drug.overview.DrugOverviewApiClient
import com.haneul.medassist.client.supplement.HealthFunctionalFoodApiClient
import com.haneul.medassist.client.llm.SupplementInteractionExplanationClient
import com.haneul.medassist.config.PublicDataCacheProperties
import com.haneul.medassist.domain.medication.DrugOverviewCoverage
import com.haneul.medassist.domain.medication.DrugOverviewLookupResult
import com.haneul.medassist.domain.medication.DrugOverviewLookupStatus
import com.haneul.medassist.domain.medication.IngredientSearchResult
import com.haneul.medassist.domain.medication.ProductSearchResult
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodLookupStatus
import com.haneul.medassist.domain.supplement.HealthFunctionalFoodSearchResult
import com.haneul.medassist.domain.supplement.SupplementProductSnapshotResult
import com.haneul.medassist.domain.supplement.SupplementSearchSourceType
import com.haneul.medassist.domain.supplement.GeneratedSupplementInteractionExplanation
import com.haneul.medassist.domain.supplement.SupplementInteractionExplanationRequest
import com.haneul.medassist.exception.GlobalExceptionHandler
import com.haneul.medassist.service.DrugOverviewCache
import com.haneul.medassist.service.DrugOverviewService
import com.haneul.medassist.service.HealthFunctionalFoodCache
import com.haneul.medassist.service.HealthFunctionalFoodService
import com.haneul.medassist.service.InMemorySupplementSearchIndexLoader
import com.haneul.medassist.service.MedicationEvidenceProvider
import com.haneul.medassist.service.MedicationEvidenceResolution
import com.haneul.medassist.service.PublicDataMedicationEvidenceProvider
import com.haneul.medassist.service.PublicDataSupplementProductEvidenceProvider
import com.haneul.medassist.service.SupplementInteractionAnalysisService
import com.haneul.medassist.service.SupplementInteractionExplanationService
import com.haneul.medassist.service.SupplementInteractionPresentationService
import com.haneul.medassist.service.SupplementNameNormalizer
import com.haneul.medassist.service.SupplementProductEvidenceProvider
import com.haneul.medassist.service.SupplementProductEvidenceResolution
import com.haneul.medassist.service.SupplementSearchIndexService
import com.haneul.medassist.support.FIXED_TIME
import com.haneul.medassist.support.officialDrugIngredient
import com.haneul.medassist.support.supplementSnapshot
import com.haneul.medassist.support.testCatalog
import com.haneul.medassist.support.verifiedDrugProduct
import com.haneul.medassist.support.verifiedRule
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class SupplementInteractionControllerTest {
    @Test
    fun `additive endpoint returns completed no-rule result without calling LLM`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-1","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.severity").value("NO_VERIFIED_RULE_FOUND"))
            .andExpect(jsonPath("$.coverage.complete").value(true))
            .andExpect(jsonPath("$.coverage.totalPairs").value(1))
            .andExpect(jsonPath("$.matchedRules.length()").value(0))
            .andExpect(jsonPath("$.explanation.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.disclaimer").isNotEmpty)
    }

    @Test
    fun `malformed official code returns validation Problem Details`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"bad code!","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.type").value("https://medassist.local/problems/validation-failed"))
    }

    @Test
    fun `empty request returns validation Problem Details`() {
        mvc().perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `syntactically valid but unknown product returns UNKNOWN evidence result`() {
        val medication = MedicationEvidenceProvider { MedicationEvidenceResolution.NotFound }

        mvc(medication).perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-404","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.processingStatus").value("FAILED"))
            .andExpect(jsonPath("$.severity").value("UNKNOWN"))
            .andExpect(jsonPath("$.failedSteps[0]").isNotEmpty)
            .andExpect(jsonPath("$.coverage.complete").value(false))
    }

    @Test
    fun `REST executes official product ingredient overview supplement and deterministic rule pipeline`() {
        val drugProductCalls = AtomicInteger()
        val ingredientCalls = AtomicInteger()
        val overviewCalls = AtomicInteger()
        val supplementCalls = AtomicInteger()
        val drugClient = object : DrugProductApiClient {
            override fun searchProducts(productName: String) = ProductSearchResult.Success(emptyList())

            override fun findProduct(productCode: String) = verifiedDrugProduct(productCode).also {
                drugProductCalls.incrementAndGet()
            }

            override fun findIngredients(productCode: String, productName: String) =
                IngredientSearchResult.Success(listOf(officialDrugIngredient("D-1", "TEST_DRUG_INGREDIENT_A"))).also {
                    ingredientCalls.incrementAndGet()
                }
        }
        val overviewClient = object : DrugOverviewApiClient {
            override fun findOverview(productCode: String, productName: String, manufacturer: String?) =
                DrugOverviewLookupResult(
                    status = DrugOverviewLookupStatus.NOT_FOUND,
                    overview = null,
                    coverage = DrugOverviewCoverage(productResolved = true, overviewResolved = false, complete = true),
                    totalCount = 0,
                    completedPages = listOf(1),
                    failedPages = emptyList(),
                    retrievedAt = FIXED_TIME,
                    providerResultCode = "00",
                ).also { overviewCalls.incrementAndGet() }
        }
        val supplementClient = object : HealthFunctionalFoodApiClient {
            override fun search(productName: String, manufacturer: String?) = HealthFunctionalFoodSearchResult(
                status = HealthFunctionalFoodLookupStatus.NOT_FOUND,
                candidates = emptyList(),
                totalCount = 0,
                completedPages = listOf(1),
                failedPages = emptyList(),
                complete = true,
                sourceType = SupplementSearchSourceType.PROVIDER,
                retrievedAt = FIXED_TIME,
            )

            override fun findByStatementNo(statementNo: String) = SupplementProductSnapshotResult(
                status = HealthFunctionalFoodLookupStatus.RESOLVED,
                snapshot = supplementSnapshot(statementNo),
                totalCount = 1,
                completedPages = listOf(1),
                failedPages = emptyList(),
                complete = true,
                retrievedAt = FIXED_TIME,
                providerResultCode = "00",
            ).also { supplementCalls.incrementAndGet() }
        }
        val cacheProperties = PublicDataCacheProperties()
        val normalizer = SupplementNameNormalizer()
        val medicationProvider = PublicDataMedicationEvidenceProvider(
            drugClient,
            DrugOverviewService(overviewClient, DrugOverviewCache(cacheProperties)),
        )
        val supplementProvider = PublicDataSupplementProductEvidenceProvider(
            HealthFunctionalFoodService(
                supplementClient,
                SupplementSearchIndexService(InMemorySupplementSearchIndexLoader(), normalizer, cacheProperties),
                normalizer,
                HealthFunctionalFoodCache(cacheProperties),
            ),
        )
        val catalog = testCatalog(rules = listOf(verifiedRule()))
        val service = SupplementInteractionAnalysisService(
            medicationProvider,
            supplementProvider,
            catalog,
            catalog,
            catalog,
            catalog,
        )
        val generatedClient = object : SupplementInteractionExplanationClient {
            override val provider = "OPENAI"
            override val model = "TEST_MODEL"
            override fun isConfigured() = true
            override fun generate(request: SupplementInteractionExplanationRequest) =
                GeneratedSupplementInteractionExplanation(
                    summary = "검수 근거 설명",
                    rationale = "제공된 Evidence만 설명",
                    consultationAdvice = "의사 또는 약사와 상담하세요.",
                )
        }
        val mvc = MockMvcBuilders.standaloneSetup(
            SupplementInteractionController(presentation(service, generatedClient)),
        )
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        mvc.perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-1","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.severity").value("CAUTION"))
            .andExpect(jsonPath("$.medication.productCode").value("P-1"))
            .andExpect(jsonPath("$.supplement.statementNo").value("S-1"))
            .andExpect(jsonPath("$.drugIngredients[0].providerCode").value("D-1"))
            .andExpect(jsonPath("$.supplementIngredients[0].canonicalId").value("CAN-1"))
            .andExpect(jsonPath("$.matchedRules[0].id").value("RULE-1"))
            .andExpect(jsonPath("$.evidence[0].sourceReferenceId").value("SRC-1"))
            .andExpect(jsonPath("$.catalogMetadata.available").value(true))
            .andExpect(jsonPath("$.coverage.complete").value(true))
            .andExpect(jsonPath("$.explanation.status").value("GENERATED"))
            .andExpect(jsonPath("$.explanation.summary").value("검수 근거 설명"))

        assertEquals(1, drugProductCalls.get())
        assertEquals(1, ingredientCalls.get())
        assertEquals(1, overviewCalls.get())
        assertEquals(1, supplementCalls.get())
    }

    @Test
    fun `available empty production-like catalog cannot return no-rule`() {
        val catalog = testCatalog(sources = emptyList(), ingredients = emptyList(), mappings = emptyList())
        val service = SupplementInteractionAnalysisService(
            medicationProvider = MedicationEvidenceProvider {
                MedicationEvidenceResolution.Resolved(
                    product = verifiedDrugProduct(),
                    ingredients = listOf(officialDrugIngredient()),
                    ingredientsComplete = true,
                    overview = null,
                )
            },
            supplementProvider = SupplementProductEvidenceProvider {
                SupplementProductEvidenceResolution.Resolved(supplementSnapshot())
            },
            sourceRepository = catalog,
            canonicalRepository = catalog,
            mappingRepository = catalog,
            ruleRepository = catalog,
        )
        val mvc = MockMvcBuilders.standaloneSetup(SupplementInteractionController(presentation(service)))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        mvc.perform(
            post("/api/v1/supplement-interaction-checks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"medicationProductCode":"P-1","supplementStatementNo":"S-1"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.severity").value("UNKNOWN"))
            .andExpect(jsonPath("$.failedSteps[0]").value("SUPPLEMENT_INGREDIENT_MAPPING_MISSING"))
            .andExpect(jsonPath("$.coverage.complete").value(false))
    }

    private fun mvc(
        medicationProvider: MedicationEvidenceProvider = MedicationEvidenceProvider {
            MedicationEvidenceResolution.Resolved(
                product = verifiedDrugProduct(),
                ingredients = listOf(officialDrugIngredient()),
                ingredientsComplete = true,
                overview = null,
            )
        },
    ): MockMvc {
        val catalog = testCatalog()
        val service = SupplementInteractionAnalysisService(
            medicationProvider = medicationProvider,
            supplementProvider = SupplementProductEvidenceProvider {
                SupplementProductEvidenceResolution.Resolved(supplementSnapshot())
            },
            sourceRepository = catalog,
            canonicalRepository = catalog,
            mappingRepository = catalog,
            ruleRepository = catalog,
        )
        return MockMvcBuilders.standaloneSetup(SupplementInteractionController(presentation(service)))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun presentation(
        analysisService: SupplementInteractionAnalysisService,
        client: SupplementInteractionExplanationClient = unavailableExplanationClient(),
    ) = SupplementInteractionPresentationService(
        analysisService,
        SupplementInteractionExplanationService(client),
    )

    private fun unavailableExplanationClient() = object : SupplementInteractionExplanationClient {
        override val provider = "OPENAI"
        override val model = "TEST_MODEL"
        override fun isConfigured() = false
        override fun generate(request: SupplementInteractionExplanationRequest) =
            GeneratedSupplementInteractionExplanation("unused", "unused", "unused")
    }
}
