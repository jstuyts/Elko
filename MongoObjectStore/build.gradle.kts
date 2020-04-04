plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":Properties"))
    implementation(project(":ServerCore"))
    implementation(project(":Trace"))
    implementation(Libraries.mongodb_driver)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
