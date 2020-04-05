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
    api(project(":Actor"))
    api(project(":Boot:Api", "default"))
    api(project(":Json"))
    api(project(":ObjectDatabase:Api"))
    api(project(":ServerCore"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
