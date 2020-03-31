plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
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
    implementation(Libraries.commons_codec)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
