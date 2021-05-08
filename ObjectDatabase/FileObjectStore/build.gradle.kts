import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":ObjectDatabase:Direct"))
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.nanojson)

    testApi(kotlin("test"))
    testImplementation(Libraries.junit_jupiter_engine)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

tasks.test {
    useJUnitPlatform()
}
