import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/squins/Squins")
    }
}

dependencies {
    api(project(":ByteIoFramer:Api"))
    api(project(":IdGeneration"))
    api(project(":Properties"))
    api(project(":Running"))
    api(project(":ServerMetadata"))
    api(project(":Timer"))
    api(project(":Trace"))

    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Rtcp"))
    implementation(project(":ByteIoFramer:WebSocket"))
    implementation(project(":Communication"))
    implementation(kotlin("stdlib-jdk8"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
