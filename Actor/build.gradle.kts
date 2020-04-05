import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

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
    api(project(":Net:Api"))
    api(project(":ServerMetadata"))
    api(project(":Trace"))

    implementation(project(":Communication"))
}

val apiClasses by tasks.registering(ApiClassesTask::class)

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
