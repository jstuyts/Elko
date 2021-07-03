import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":ByteIoFramer:Api"))
    api(project(":IdGeneration"))
    api(project(":Properties"))
    api(project(":Running"))
    api(project(":ServerMetadata"))
    api(project(":Trace"))
    api(project(":Util"))
    api(Libraries.ooverkommelig)

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
