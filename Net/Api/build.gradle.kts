import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":ByteIoFramer:Api"))
    api(project(":Properties"))
    api(project(":Running"))
    api(project(":ServerMetadata"))
    api(project(":Timer"))
    api(project(":Trace"))

    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Rtcp"))
    implementation(project(":ByteIoFramer:WebSocket"))
    implementation(project(":ScalableSsl"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
