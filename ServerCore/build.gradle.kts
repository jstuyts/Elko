import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Actor"))
    api(project(":IdGeneration"))
    api(project(":JsonMessageHandling"))
    api(project(":Net:Api"))
    api(project(":Net:ConnectionRetrier"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":ServerMetadata"))
    api(project(":Timer"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Communication"))
    implementation(project(":ObjectDatabase:Direct"))
    implementation(project(":ObjectDatabase:Repository"))
    implementation(project(":Running"))
    implementation(project(":Util"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

val generateBuildVersionClassSourceFile by tasks.registering {
    inputs.property("version", project.version)
    outputs.dir(temporaryDir)

    doLast {
        val packageDirectory = File(temporaryDir, "org/elkoserver/foundation/server")
        packageDirectory.mkdirs()
        val buildVersionClassSourceFile = File(packageDirectory, "BuildVersion.kt")
        buildVersionClassSourceFile.writeText("""package org.elkoserver.foundation.server

object BuildVersion {
    const val version = "${project.version}"
}
""")
    }
}

tasks.withType<KotlinCompile>().forEach { task -> task.setDependsOn(listOf(generateBuildVersionClassSourceFile)) }

kotlin {
    sourceSets["main"].apply {
        kotlin.srcDir(generateBuildVersionClassSourceFile)
    }
}

/* Groovy to port:
task sourcesJar(type: Jar) {
    classifier = 'sources'
    from(sourceSets.main.allJava)
}

publishing {
    publications {
        maven(MavenPublication) {
            from(components.java)
            artifact(sourcesJar)
        }
    }
}
*/
