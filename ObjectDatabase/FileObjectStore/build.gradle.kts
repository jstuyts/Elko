import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":ObjectDatabase:Local"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(Libraries.nanojson)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
