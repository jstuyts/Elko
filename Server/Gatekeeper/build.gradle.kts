import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Actor"))
    api(project(":Boot:Api", "default"))
    api(project(":JsonMessageHandling"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":ServerCore"))
    api(project(":ServerMetadata"))
    api(project(":Trace"))
    api(project(":Timer"))
    api(Libraries.nanojson)

    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Websocket"))
    implementation(project(":Communication"))
    implementation(project(":Net:Http"))
    implementation(project(":Net:Rtcp"))
    implementation(project(":Net:Tcp"))
    implementation(project(":Net:Websocket"))
    implementation(project(":Net:Zeromq"))
    implementation(project(":ObjectDatabase:PropertiesBasedObjectDatabase"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
