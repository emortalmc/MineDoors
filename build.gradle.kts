import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    java
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://jitpack.io")
//    maven("https://repo.hypera.dev/snapshots/")
}

dependencies {
    implementation("com.github.EmortalMC", "Minestom", "e195ce7a95")
//    implementation("com.github.Minestom", "Minestom", "fc90fe8852")

    implementation("dev.emortal.immortal:Immortal:3.0.1")

    //implementation("com.github.emortaldev:Scaffolding:aa0cecf7e5")
    implementation("com.github.hollow-cube.common:schem:fffa7f431b")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    implementation("org.tinylog:tinylog-api-kotlin:2.5.0")
    implementation("org.tinylog:tinylog-impl:2.5.0")
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        manifest {
            attributes (
                "Main-Class" to "dev.emortal.doors.MainKt",
                "Multi-Release" to true
            )
        }

        transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer::class.java)

        mergeServiceFiles()

        archiveBaseName.set("doors")
    }

    build { dependsOn(shadowJar) }

    withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}