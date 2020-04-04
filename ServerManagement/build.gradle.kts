plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":Boot:App", "default"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
