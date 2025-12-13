plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow")
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

val flinkVersion = "1.20.0"
val flinkConnectorKafkaVersion = "3.1.0-1.18"
val kafkaVersion = "3.7.0"

dependencies {
    // Common module with shared event schemas
    implementation(project(":common"))

    // Flink core dependencies
    implementation("org.apache.flink:flink-streaming-java:$flinkVersion")
    implementation("org.apache.flink:flink-clients:$flinkVersion")

    // Kafka connector
    implementation("org.apache.flink:flink-connector-kafka:$flinkConnectorKafkaVersion")

    // JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.11")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.22.1")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

application {
    mainClass.set("com.example.flink.EventProcessorJob")
}

tasks {
    shadowJar {
        archiveBaseName.set("flink-job")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        // Exclude signature files that cause issues
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

        // Relocate dependencies to avoid conflicts
        relocate("com.google", "shade.com.google")
    }

    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }
}

// Explicitly declare dependencies on shadowJar to fix Gradle warning.
tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}

kotlin {
    jvmToolchain(11)
}
