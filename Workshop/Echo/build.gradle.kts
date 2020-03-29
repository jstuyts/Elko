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
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":Server:Workshop"))
    implementation(project(":ServerCore"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}
