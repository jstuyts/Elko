plugins {
    java
    kotlin("jvm")
}

repositories {
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/squins/Squins")
    }
}

dependencies {
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(Libraries.mongodb_driver)
    implementation(Libraries.nanojson)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}
