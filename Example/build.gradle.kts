plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerCore"))
    implementation(Libraries.mongodb_driver)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
