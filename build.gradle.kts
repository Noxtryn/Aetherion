plugins {
    id("net.fabricmc.fabric-loom") version "1.17.17"
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("maven-publish")
}

val mcTarget = project.property("mc_target") as String
fun targetProp(key: String) = project.property("target.$mcTarget.$key") as String

version = "${project.property("mod_version")}+$mcTarget"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

// Optional per-target source set (src/mc26.2/kotlin, src/mc26.3/kotlin) for version-specific adapters.
sourceSets.main {
    kotlin.srcDir("src/mc$mcTarget/kotlin")
    resources.srcDir("src/mc$mcTarget/resources")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://jitpack.io")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:${targetProp("minecraft")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${targetProp("fabric_api")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")
    compileOnly("com.terraformersmc:modmenu:${targetProp("modmenu")}")
    runtimeOnly("com.terraformersmc:modmenu:${targetProp("modmenu")}") // dev client only: exercises the ModMenu entrypoint
}

// Optional dev window size for runClient: -Pdev_width=1280 -Pdev_height=720
loom {
    runs {
        named("client") {
            providers.gradleProperty("dev_width").orNull?.let { programArgs("--width", it) }
            providers.gradleProperty("dev_height").orNull?.let { programArgs("--height", it) }
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcTarget", mcTarget)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "mcTarget" to mcTarget)
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
