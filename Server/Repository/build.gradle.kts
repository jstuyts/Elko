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
    implementation(project(":Actor"))
    implementation(project(":Boot"))
    implementation(project(":FileObjectStore"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":ServerCore"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
