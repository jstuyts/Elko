plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":Boot:App", "default"))
}

val apiClasses by tasks.registering(org.elkoserver.develop.gradle.apiclasses.ApiClassesTask::class)

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
