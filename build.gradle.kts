buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.3.72"))
    }
}

plugins {
    id("com.gitlab.stfs.gradle.dependency-graph-plugin") version "0.3"
}

group = "org.elko"
version = "2.0.4-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "6.3"
    distributionType = Wrapper.DistributionType.ALL
}
