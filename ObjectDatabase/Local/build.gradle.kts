plugins {
    `java-library`
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Json"))
    api(project(":JsonMessageHandling"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Running"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(org.elkoserver.develop.gradle.apiclasses.ApiClassesTask::class)

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
