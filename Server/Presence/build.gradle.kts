import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Actor"))
    api(project(":Boot:Api", "default"))
    api(project(":ObjectDatabase:Api"))
    api(project(":ServerCore"))
    api(project(":Properties"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
