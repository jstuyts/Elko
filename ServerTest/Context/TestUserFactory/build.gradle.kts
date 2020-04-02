plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Json"))
    implementation(project(":Actor"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":Properties"))
    implementation(project(":Server:Context"))
    implementation(project(":Trace"))
    implementation(Libraries.commons_codec)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
