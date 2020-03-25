plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        url = uri("http://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":Communication"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
