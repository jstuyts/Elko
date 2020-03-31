plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
