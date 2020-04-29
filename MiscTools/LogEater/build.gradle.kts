plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation(Libraries.mongodb_driver)
    implementation(Libraries.nanojson)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
