buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.3.72"
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

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
