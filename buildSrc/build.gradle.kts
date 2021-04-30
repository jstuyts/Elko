import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.4.32"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation("org.javassist:javassist:3.27.0-GA")
    implementation("org.mongodb:mongodb-driver:3.4.3")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}
