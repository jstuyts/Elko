plugins {
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Properties"))
    implementation(project(":Trace"))
    implementation(kotlin("stdlib-jdk8"))
}
