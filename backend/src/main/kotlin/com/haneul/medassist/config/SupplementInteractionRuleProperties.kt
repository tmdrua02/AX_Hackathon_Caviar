package com.haneul.medassist.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("medassist.supplement-interaction-rules")
data class SupplementInteractionRuleProperties(
    var resource: String = "classpath:supplement-interaction-rules.json",
)
