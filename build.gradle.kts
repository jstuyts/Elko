buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.10"))
    }
}

plugins {
    id("com.gitlab.stfs.gradle.dependency-graph-plugin") version "0.4"
}

group = "org.elko"
version = "2.0.4-SNAPSHOT"

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "6.7"
    distributionType = Wrapper.DistributionType.ALL
}

subprojects {
    repositories {
        jcenter()
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