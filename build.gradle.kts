plugins {
    kotlin("jvm") version "2.1.21"
}

group = "com.kayar.jclec"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Maven repository access
    implementation("org.apache.maven.resolver:maven-resolver-api:1.9.16")
    implementation("org.apache.maven.resolver:maven-resolver-impl:1.9.16")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.16")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.16")
    implementation("org.apache.maven:maven-resolver-provider:3.9.5")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
