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

val kafkaVersion = "3.7.0"

dependencies {
    // Common module with shared event schemas
    implementation(project(":common"))

    // Kafka client
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    // JSON serialization (inherited from common but explicit here)
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
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

application {
    mainClass.set("com.example.producer.EventProducer")
}

tasks {
    shadowJar {
        archiveBaseName.set("event-producer")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        // Exclude signature files that cause issues
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }
}

kotlin {
    jvmToolchain(11)
}
