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
    implementation("org.javassist:javassist:3.27.0-GA")
    implementation(kotlin("stdlib-jdk8"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
