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
    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Json"))
    implementation(project(":Trace"))
    implementation("commons-codec:commons-codec:1.9")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
