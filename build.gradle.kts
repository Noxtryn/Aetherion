plugins {
    id("net.fabricmc.fabric-loom") version "1.17.17"
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://jitpack.io")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")
    compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    val archivesName = project.base.archivesName.get()
    from("LICENSE") {
        rename { "${it}_$archivesName" }
    }
}
