plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Json"))
    api(project(":JsonMessageHandling"))
}

val apiClasses by tasks.registering(org.elkoserver.develop.gradle.apiclasses.ApiClassesTask::class)

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
