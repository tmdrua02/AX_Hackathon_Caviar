import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.haneul"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.jpa)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.cache)
    implementation(libs.springdoc)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    implementation(libs.caffeine)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.security.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
    workingDir = rootProject.projectDir
}

tasks.named<BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
