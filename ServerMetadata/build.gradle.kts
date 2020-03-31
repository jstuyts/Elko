plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
