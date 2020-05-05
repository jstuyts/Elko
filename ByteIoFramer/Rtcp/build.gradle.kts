import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":ByteIoFramer:Api"))

    implementation(project(":Communication"))
    implementation(project(":Json"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.nanojson)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
