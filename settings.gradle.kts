rootProject.name = "McRPG"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }

    // Declare plugin versions for project builds here
    plugins {
        // Read loomVersion from gradle.properties
        val loomVersion = providers.gradleProperty("loomVersion")
            .orElse("1.11-SNAPSHOT") // optional fallback
            .get()

        id("fabric-loom") version loomVersion
    }
}
