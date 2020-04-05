import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":JsonMessageHandling"))
    api(project(":Net:Api"))
    api(project(":Server:Context"))

    implementation(project(":Actor"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation(Libraries.jeromq)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
