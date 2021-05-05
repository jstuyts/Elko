buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.5.0"))
    }
}

plugins {
    id("com.gitlab.stfs.gradle.dependency-graph-plugin") version "0.4"
}

group = "org.elko"
version = "2.0.4-SNAPSHOT"

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "7.0"
    distributionType = Wrapper.DistributionType.ALL
}

subprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://dl.bintray.com/squins/Squins")
        }
        mavenLocal {
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}