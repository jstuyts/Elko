import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Boot:Api", "default"))
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(Libraries.junit_jupiter_api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(Libraries.junit_jupiter_engine)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.test {
    useJUnitPlatform()
}
