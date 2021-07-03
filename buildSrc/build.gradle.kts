import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.5.20"
}

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/org.javassist/javassist
    implementation("org.javassist:javassist:3.28.0-GA")
    // https://mvnrepository.com/artifact/org.mongodb/mongodb-driver
    implementation("org.mongodb:mongodb-driver:3.4.3")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}
