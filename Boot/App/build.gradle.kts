import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Properties"))
    api(project(":Trace"))

    implementation(project(":Boot:Api", "default"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.logback_classic)

    testImplementation(Libraries.junit_jupiter_api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(Libraries.junit_jupiter_engine)
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
