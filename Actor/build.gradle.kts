import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":JsonMessageHandling"))
    api(project(":Net:Api"))
    api(project(":ServerMetadata"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    api(Libraries.nanojson)
    implementation(project(":Communication"))
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
