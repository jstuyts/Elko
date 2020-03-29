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
    implementation(project(":Json"))
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation("org.mongodb:mongodb-driver:3.4.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
