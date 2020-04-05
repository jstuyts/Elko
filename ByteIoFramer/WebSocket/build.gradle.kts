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
}

val apiClasses by tasks.registering(org.elkoserver.develop.gradle.apiclasses.ApiClassesTask::class)

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
