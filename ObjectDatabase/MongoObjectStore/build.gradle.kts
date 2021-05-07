import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":ObjectDatabase:Direct"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.mongodb_driver)
    implementation(Libraries.nanojson)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}
