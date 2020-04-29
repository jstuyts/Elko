import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Boot:Api", "default"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Actor"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":ServerCore"))
    implementation(project(":Timer"))
    implementation(project(":Util"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
