plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":Communication"))
    implementation(project(":Json"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
