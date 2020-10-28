plugins {
    java
}

dependencies {
    implementation(project(":Json"))
    implementation(Libraries.nanojson)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
