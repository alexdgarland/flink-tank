plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.gradleup.shadow") version "9.1.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}
