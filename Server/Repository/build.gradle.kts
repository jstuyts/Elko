import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/squins/Squins")
    }
}

dependencies {
    api(project(":Boot:Api", "default"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Actor"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":ServerCore"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
