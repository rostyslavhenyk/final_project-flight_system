plugins {
    application
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "2.3.11"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "uk.ac.leeds.comp2850"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.11"
val logbackVersion = "1.4.14"
val pebbleVersion = "3.2.2"

dependencies {
    // Ktor server core
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")

    // Pebble templating
    implementation("io.pebbletemplates:pebble:$pebbleVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // CSV handling
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21")
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    // Enable development mode for hot reload
    systemProperty("io.ktor.development", "true")
}

kotlin {
    jvmToolchain(21)
}

// Code quality: Detekt (static analysis)
// Reports violations as warnings, doesn't fail build
detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = true // Report but don't fail build
}

// Code quality: ktlint (code style)
// Reports violations as warnings, doesn't fail build
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ignoreFailures.set(true) // Report but don't fail build
}
