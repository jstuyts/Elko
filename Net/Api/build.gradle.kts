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
    implementation(project(":Boot"))
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Rtcp"))
    implementation(project(":ByteIoFramer:WebSocket"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Running"))
    implementation(project(":ScalableSsl"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Timer"))
    implementation(project(":Trace"))
    implementation("commons-codec:commons-codec:1.9")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
