package com.haneul.medassist.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
class RestClientConfig {
    @Bean
    @Qualifier("drugProductRestClient")
    fun drugProductRestClient(
        properties: DrugProductApiProperties,
    ): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.policy.connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.policy.readTimeout)
        }

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
