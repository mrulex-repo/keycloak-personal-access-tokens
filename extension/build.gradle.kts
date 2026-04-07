plugins {
    java
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.mrulex.keycloak.plugin"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val keycloakVersion = "26.3.4"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.keycloak:keycloak-core:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    implementation("com.password4j:password4j:1.8.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.testcontainers:testcontainers:1.21.3")
    testImplementation("com.github.dasniko:testcontainers-keycloak:3.9.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.test {
    dependsOn(tasks.shadowJar)
    useJUnitPlatform()
}
