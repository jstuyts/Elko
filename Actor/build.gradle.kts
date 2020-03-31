plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Communication"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Trace"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
