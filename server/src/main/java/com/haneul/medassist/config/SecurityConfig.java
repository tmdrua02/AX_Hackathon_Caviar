package com.haneul.medassist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    @Profile("mock")
    SecurityFilterChain mockSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.contentTypeOptions(options -> {}))
                .build();
    }

    @Bean
    @Profile("!mock")
    SecurityFilterChain secureByDefault(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // JWT/OIDC 어댑터를 붙이기 전에는 모든 운영 API를 HTTP Basic으로 보호한다.
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
