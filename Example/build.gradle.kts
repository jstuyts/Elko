import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":JsonMessageHandling"))
    api(project(":Server:Context"))
    api(Libraries.nanojson)
    implementation(project(":Feature:BasicExamples"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
