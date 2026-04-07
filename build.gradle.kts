plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
    id("jacoco")
}

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
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-rate-limit-jvm")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.github.InfinityLoop1308.PipePipeExtractor:extractor:cc497b3becb980013d6503b349ff1a5facef6ecb")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE")
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("com.password4j:password4j:1.7.1")
    implementation("com.auth0:java-jwt:4.4.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("io.ktor:ktor-server-content-negotiation-jvm")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("network")
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
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
    jvmToolchain(21)
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
