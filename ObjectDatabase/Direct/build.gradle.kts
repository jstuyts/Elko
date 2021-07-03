import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":JsonMessageHandling"))
    api(project(":Net:ConnectionRetrier"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":Trace"))
    api(Libraries.nanojson)
    api(Libraries.ooverkommelig)

    implementation(project(":Running"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
