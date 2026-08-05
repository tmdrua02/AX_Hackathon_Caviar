package com.haneul.medassist

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class MedassistBackendApplication

fun main(args: Array<String>) {
	runApplication<MedassistBackendApplication>(*args)
}
