buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.4.0"))
    }
}

plugins {
    id("com.gitlab.stfs.gradle.dependency-graph-plugin") version "0.3"
}

group = "org.elko"
version = "2.0.4-SNAPSHOT"

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "6.6"
    distributionType = Wrapper.DistributionType.ALL
}
