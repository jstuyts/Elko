plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation(Libraries.mongodb_driver)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
