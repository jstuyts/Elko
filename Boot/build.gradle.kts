plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
