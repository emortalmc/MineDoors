import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    java
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.hypera.dev/snapshots/")
}

dependencies {
    implementation("com.github.EmortalMC", "Minestom", "0657315f62")
//    implementation("com.github.Minestom", "Minestom", "fc90fe8852")

    api("com.github.EmortalMC:KStom:50b2b882fa")
    api("com.github.emortaldev:Particable:fadfbe0213")

    implementation("com.github.EmortalMC:Immortal:ba273bb925")

    implementation("dev.hypera:Scaffolding:0.1.3-SNAPSHOT")
    implementation("com.github.EmortalMC:Rayfast:684e854a48")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")

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