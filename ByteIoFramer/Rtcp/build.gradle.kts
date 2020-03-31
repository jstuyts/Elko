plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":Communication"))
    implementation(project(":Json"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
