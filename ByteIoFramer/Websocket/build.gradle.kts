import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":ByteIoFramer:Api"))

    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Json"))
    implementation(project(":Util"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.nanojson)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
