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
    implementation(project(":Actor"))
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":Running"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerCore"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Trace"))
    implementation("org.zeromq:jeromq:0.5.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
