plugins {
    `java-library`
}

dependencies {
    implementation(project(":Properties"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
