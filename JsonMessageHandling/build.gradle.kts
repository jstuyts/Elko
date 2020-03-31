plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(project(":Trace"))
    implementation(Libraries.commons_codec)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
