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

    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Json"))
    implementation(project(":Util"))
    implementation(Libraries.commons_codec)
    implementation(Libraries.nanojson)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
