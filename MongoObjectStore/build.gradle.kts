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
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation("org.mongodb:mongodb-driver:3.4.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
