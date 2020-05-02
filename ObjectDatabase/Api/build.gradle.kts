import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":JsonMessageHandling"))
    api(Libraries.nanojson)

    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
