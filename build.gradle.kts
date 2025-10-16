val minecraftVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project   // <-- match the prop name
val yarnMappings: String by project
val modVersion: String by project

plugins {
    id("fabric-loom")
    id("java")
}

group = "com.github.beemerwt"
version = modVersion

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    compileOnly("org.jspecify:jspecify:1.0.0")
    implementation("blue.endless:jankson:1.2.1")
    include("blue.endless:jankson:1.2.1")

    compileOnly("org.geysermc.geyser:api:2.8.3-SNAPSHOT")

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    include("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.test { useJUnitPlatform() }
