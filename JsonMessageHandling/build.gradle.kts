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
    implementation(project(":Trace"))
    implementation("commons-codec:commons-codec:1.9")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
