plugins {
    application
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "2.3.12"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "uk.ac.leeds.comp2850"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.4.14"
val pebbleVersion = "3.2.2"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("com.twilio.sdk:twilio:10.1.5")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    // pebble templating
    implementation("io.pebbletemplates:pebble:$pebbleVersion")

    // logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // csv handling
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")

    // database
    implementation("org.jetbrains.exposed:exposed-core:0.49.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.49.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.49.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // testing
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
    systemProperty("io.ktor.development", "true")
}

kotlin {
    jvmToolchain(21)
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = true
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ignoreFailures.set(true)
}
