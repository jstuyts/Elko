plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Trace"))
    implementation(project(":Util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
