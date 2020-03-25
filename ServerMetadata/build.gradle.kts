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
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
