plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Boot:Api", "default"))
    implementation(project(":Properties"))
    implementation(project(":Trace"))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(Libraries.junit_jupiter_api)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(Libraries.junit_jupiter_engine)
}

tasks.test {
    useJUnitPlatform()
}
