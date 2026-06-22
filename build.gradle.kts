plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("io.ktor.plugin") version "3.5.0"
    id("jacoco")
}

apply(from = "gradle/openapi-validation.gradle.kts")
group = "dev.typetype"
version = "0.0.1"
application {
    mainClass.set("dev.typetype.server.ApplicationKt")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-websockets-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-rate-limit-jvm")
    implementation("ch.qos.logback:logback-classic:1.5.34")
    implementation("com.github.InfinityLoop1308.PipePipeExtractor:extractor:e4b13eed86d83ae3ce309eb16e9e7460024a4d7b")
    implementation("org.json:json:20260522")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("io.lettuce:lettuce-core:7.6.0.RELEASE")
    implementation("org.jetbrains.exposed:exposed-core:1.3.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("com.password4j:password4j:1.8.4")
    implementation("com.auth0:java-jwt:4.5.2")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("io.ktor:ktor-server-content-negotiation-jvm")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
}
val buildInfoVersion = version.toString().trim().takeUnless { it.isBlank() || it == "unspecified" } ?: "0.0.0-dev"
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/sources/buildInfo/main")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    inputs.property("version", buildInfoVersion)
    outputs.dir(generatedBuildInfoDir)
    doLast {
        val output = generatedBuildInfoDir.get().file("dev/typetype/server/BuildInfo.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            package dev.typetype.server

            object BuildInfo {
                const val VERSION: String = "${buildInfoVersion.replace("\\", "\\\\").replace("\"", "\\\"")}"
            }
        """.trimIndent())
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("network")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.20".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("main") { kotlin.srcDir(generatedBuildInfoDir) }
}

tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
