package com.haneul.medassist.config

import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataCallExecutorFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import com.haneul.medassist.client.llm.LlmCallExecutor

@Configuration
class RestClientConfig {
    @Bean
    fun publicDataCallExecutorFactory(): PublicDataCallExecutorFactory = PublicDataCallExecutorFactory()

    @Bean
    @Qualifier("drugProductCallExecutor")
    fun drugProductCallExecutor(
        properties: DrugProductApiProperties,
        factory: PublicDataCallExecutorFactory,
    ): PublicDataCallExecutor = factory.create(properties.client)

    @Bean
    @Qualifier("durCallExecutor")
    fun durCallExecutor(
        properties: DurApiProperties,
        factory: PublicDataCallExecutorFactory,
    ): PublicDataCallExecutor = factory.create(properties.client)

    @Bean
    @Qualifier("drugOverviewCallExecutor")
    fun drugOverviewCallExecutor(
        properties: DrugOverviewApiProperties,
        factory: PublicDataCallExecutorFactory,
    ): PublicDataCallExecutor = factory.create(properties.client)

    @Bean
    @Qualifier("healthFunctionalFoodCallExecutor")
    fun healthFunctionalFoodCallExecutor(
        properties: HealthFunctionalFoodApiProperties,
        factory: PublicDataCallExecutorFactory,
    ): PublicDataCallExecutor = factory.create(properties.client)

    @Bean
    @Qualifier("llmCallExecutor")
    fun llmCallExecutor(properties: OpenAiExplanationProperties): LlmCallExecutor = LlmCallExecutor(properties)

    @Bean
    @Qualifier("drugProductRestClient")
    fun drugProductRestClient(
        properties: DrugProductApiProperties,
    ): RestClient = restClient(properties.client)

    @Bean
    @Qualifier("durRestClient")
    fun durRestClient(
        properties: DurApiProperties,
    ): RestClient = restClient(properties.client)

    @Bean
    @Qualifier("drugOverviewRestClient")
    fun drugOverviewRestClient(
        properties: DrugOverviewApiProperties,
    ): RestClient = restClient(properties.client)

    @Bean
    @Qualifier("healthFunctionalFoodRestClient")
    fun healthFunctionalFoodRestClient(
        properties: HealthFunctionalFoodApiProperties,
    ): RestClient = restClient(properties.client)

    @Bean
    @Qualifier("openAiExplanationRestClient")
    fun openAiExplanationRestClient(properties: OpenAiExplanationProperties): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.readTimeout)
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    private fun restClient(policy: PublicDataClientPolicy): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(policy.connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(policy.readTimeout)
        }

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
