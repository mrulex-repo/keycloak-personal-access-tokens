import com.diffplug.spotless.LineEnding
import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    java
    jacoco
    checkstyle
    id("com.github.spotbugs")
    id("com.gradleup.shadow")
    id("org.owasp.dependencycheck")
    id("org.openrewrite.rewrite")
    id("com.diffplug.spotless")
}

group = "com.mrulex.keycloak.plugin"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.keycloak:keycloak-core:${project.extra["keycloakVersion"]}")
    compileOnly("org.keycloak:keycloak-server-spi:${project.extra["keycloakVersion"]}")
    compileOnly("org.keycloak:keycloak-server-spi-private:${project.extra["keycloakVersion"]}")
    compileOnly("org.keycloak:keycloak-services:${project.extra["keycloakVersion"]}")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    implementation("com.password4j:password4j:${project.extra["password4jVersion"]}")

    compileOnly("org.projectlombok:lombok:${project.extra["lombokVersion"]}")
    annotationProcessor("org.projectlombok:lombok:${project.extra["lombokVersion"]}")
    testCompileOnly("org.projectlombok:lombok:${project.extra["lombokVersion"]}")
    testAnnotationProcessor("org.projectlombok:lombok:${project.extra["lombokVersion"]}")

    testImplementation("org.junit.jupiter:junit-jupiter:${project.extra["junitJupiterVersion"]}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${project.extra["junitPlatformLauncherVersion"]}")
    testImplementation("org.assertj:assertj-core:${project.extra["assertjVersion"]}")
    testImplementation("org.testcontainers:testcontainers:${project.extra["testContainersVersion"]}")
    testImplementation("com.github.dasniko:testcontainers-keycloak:${project.extra["testContainersKeycloakVersion"]}")
    testImplementation("org.mockito:mockito-core:${project.extra["mockitoVersion"]}")
    testImplementation("org.jacoco:org.jacoco.core:${project.extra["jacocoVersion"]}")

    rewrite(platform("org.openrewrite:rewrite-bom:8.75.2"))
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

jacoco {
    toolVersion = project.extra["jacocoVersion"] as String
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    doFirst {
        // configurations.jacocoAgent is a wrapper JAR; the real agent is jacocoagent.jar inside it.
        val agentDir = layout.buildDirectory.dir("jacocoAgent").get().asFile
        agentDir.mkdirs()
        copy {
            from(zipTree(configurations.jacocoAgent.get().singleFile))
            include("jacocoagent.jar")
            into(agentDir)
        }
        systemProperty("jacocoAgentJar", File(agentDir, "jacocoagent.jar").absolutePath)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    // Merge local exec + remote dumps from Keycloak containers
    executionData(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    // Same exec files as the report
    executionData(fileTree(layout.buildDirectory.dir("jacoco")).include("*.exec"))

    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }

        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }

    // Lombok-generated code is excluded via lombok.config (addLombokGeneratedAnnotation)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/config/**")
            }
        })
    )
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

checkstyle {
    toolVersion = "10.20.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle> {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

rewrite {
    activeStyle()
    configFile = project.file("config/openrewrite/openrewrite.yml")
    activeRecipe("com.mrulex.StyleFixes")
    setExportDatatables(true)
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.35.0")
        removeUnusedImports()
        lineEndings = LineEnding.UNIX
    }
}

spotbugs {
    ignoreFailures.set(false)
    showStackTraces.set(true)
    showProgress.set(true)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
}

tasks.withType<SpotBugsTask> {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "config/owasp/suppressions.xml"
    analyzers.apply {
        assemblyEnabled = false
        nodeEnabled = false
    }
    nvd.apply {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}
