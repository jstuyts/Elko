buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.3.71"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
