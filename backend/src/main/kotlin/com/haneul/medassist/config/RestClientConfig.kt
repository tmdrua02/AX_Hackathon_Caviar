package com.haneul.medassist.config

import com.haneul.medassist.client.common.PublicDataCallExecutor
import com.haneul.medassist.client.common.PublicDataCallExecutorFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

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
    @Qualifier("drugProductRestClient")
    fun drugProductRestClient(
        properties: DrugProductApiProperties,
    ): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.client.connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.client.readTimeout)
        }

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
