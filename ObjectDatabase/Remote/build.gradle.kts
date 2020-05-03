import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/squins/Squins")
    }
}

dependencies {
    api(project(":Net:Api"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":ServerMetadata"))
    api(project(":Timer"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    implementation(project(":Actor"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
