import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Boot:Api", "default"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Actor"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Websocket"))
    implementation(project(":Communication"))
    implementation(project(":Net:Http"))
    implementation(project(":Net:Rtcp"))
    implementation(project(":Net:Tcp"))
    implementation(project(":Net:Websocket"))
    implementation(project(":Net:Zeromq"))
    implementation(project(":ObjectDatabase:PropertiesBasedObjectDatabase"))
    implementation(project(":OrdinalGeneration"))
    implementation(project(":ServerCore"))
    implementation(project(":Util"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
