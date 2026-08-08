plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.haneul"
version = "0.0.1-SNAPSHOT"

springBoot {
	mainClass.set("com.haneul.medassist.MedassistBackendApplicationKt")
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.dataformat:jackson-dataformat-xml")
	implementation("tools.jackson.module:jackson-module-kotlin")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("external-api")
		excludeTags("external-llm")
	}
}

tasks.register<Test>("externalLlmTest") {
	description = "Runs an opt-in OpenAI presentation test with synthetic evidence only."
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("external-llm")
	}
	shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("externalApiTest") {
	description = "Runs opt-in tests against external public data APIs."
	group = "verification"
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	useJUnitPlatform {
		includeTags("external-api")
	}
	shouldRunAfter(tasks.named("test"))
}

tasks.register<JavaExec>("validateSupplementRuleCatalog") {
	description = "Validates a supplement rule catalog and writes a data-quality report."
	group = "verification"
	dependsOn(tasks.named("classes"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.haneul.medassist.repository.SupplementRuleCatalogCli")
	doFirst {
		val catalogPath = providers.gradleProperty("catalogPath").orNull
			?: error("-PcatalogPath is required")
		val reportPath = providers.gradleProperty("reportPath").orNull
			?: layout.buildDirectory.file("reports/supplement-rule-catalog-validation.json").get().asFile.absolutePath
		args("--mode=validate", "--catalog=$catalogPath", "--report=$reportPath")
	}
}

tasks.register<JavaExec>("buildVerifiedSupplementRuleCatalog") {
	description = "Builds a reviewer-approved supplement rule catalog without modifying its source file."
	group = "build"
	dependsOn(tasks.named("classes"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.haneul.medassist.repository.SupplementRuleCatalogCli")
	doFirst {
		val catalogPath = providers.gradleProperty("catalogPath").orNull
			?: error("-PcatalogPath is required")
		val reviewer = providers.gradleProperty("reviewer").orNull
			?: error("-Previewer is required")
		val catalogVersion = providers.gradleProperty("catalogVersion").orNull
			?: error("-PcatalogVersion is required")
		val outputPath = providers.gradleProperty("outputPath").orNull
			?: error("-PoutputPath is required")
		val reportPath = providers.gradleProperty("reportPath").orNull
			?: layout.buildDirectory.file("reports/supplement-rule-catalog-validation.json").get().asFile.absolutePath
		val generatedBy = providers.gradleProperty("generatedBy").orNull ?: reviewer
		args(
			"--mode=build-verified",
			"--catalog=$catalogPath",
			"--report=$reportPath",
			"--reviewer=$reviewer",
			"--catalog-version=$catalogVersion",
			"--output=$outputPath",
			"--generated-by=$generatedBy",
		)
	}
}
