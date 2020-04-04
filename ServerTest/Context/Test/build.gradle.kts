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
    implementation(project(":Server:Context"))
    implementation(project(":ServerCore"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
