import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Actor"))
    api(project(":Boot:Api", "default"))
    api(project(":ObjectDatabase:Api"))
    api(project(":ServerCore"))
    api(project(":Properties"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Websocket"))
    implementation(project(":Communication"))
    implementation(project(":Net:Http"))
    implementation(project(":Net:Rtcp"))
    implementation(project(":Net:Tcp"))
    implementation(project(":Net:Websocket"))
    implementation(project(":Net:Zeromq"))
    implementation(project(":ObjectDatabase:Direct"))
    implementation(project(":ObjectDatabase:Repository"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
