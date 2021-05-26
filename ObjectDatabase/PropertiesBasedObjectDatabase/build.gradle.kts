import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":Properties"))
    api(project(":JsonMessageHandling"))
    api(project(":Net:ConnectionRetrier"))
    api(project(":Running"))
    api(project(":ServerCore"))
    api(project(":ServerMetadata"))
    api(project(":Trace"))
    api(Libraries.ooverkommelig)

    implementation(project(":Communication"))
    implementation(project(":ObjectDatabase:Direct"))
    implementation(project(":ObjectDatabase:Repository"))
    implementation(kotlin("stdlib-jdk8"))

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
