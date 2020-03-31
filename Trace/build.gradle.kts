plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Boot:Api"))
    implementation(project(":Json"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
