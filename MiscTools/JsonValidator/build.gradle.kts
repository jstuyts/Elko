plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(Libraries.nanojson)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
