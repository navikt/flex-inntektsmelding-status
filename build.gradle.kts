import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.spring") version "1.7.20"
}

group = "no.nav.helse.flex"
version = "1.0.0"
description = "flex-inntektsmelding-status"
java.sourceCompatibility = JavaVersion.VERSION_17

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()

    maven(url = "https://packages.confluent.io/maven/")

    maven(url = "https://jitpack.io")

    maven {
        url = uri("https://maven.pkg.github.com/navikt/maven-release")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

val testContainersVersion = "1.17.5"
val logstashLogbackEncoderVersion = "7.2"
val kluentVersion = "1.72"
val brukernotifikasjonAvroVersion = "2.5.2"
val confluentVersion = "7.3.0"
val sykepengesoknadKafkaVersion = "2022.10.28-10.24-aa0eced7"

dependencies {
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonAvroVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:kafka:$testContainersVersion")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    this.archiveFileName.set("app.jar")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
        if (System.getenv("CI") == "true") {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("STANDARD_OUT", "STARTED", "PASSED", "FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
