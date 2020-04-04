plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
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
    sourceCompatibility = JavaVersion.VERSION_12
}
