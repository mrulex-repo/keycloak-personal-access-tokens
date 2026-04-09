rootProject.name = "keycloak-personal-access-tokens"

pluginManagement {
    fun gradleProperty(name: String): String = providers.gradleProperty(name).get()

    plugins {
        id("com.gradleup.shadow") version gradleProperty("shadowVersion")
        id("com.diffplug.spotless") version gradleProperty("spotlessVersion")
        id("com.github.spotbugs") version gradleProperty("spotbugsVersion")
        id("org.openrewrite.rewrite") version gradleProperty("openrewriteGradlePluginVersion")
        id("org.owasp.dependencycheck") version gradleProperty("dependencycheckVersion")
    }
}
