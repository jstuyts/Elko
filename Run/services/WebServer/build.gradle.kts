import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.argparse4j)
    implementation(Libraries.jetty_server)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
