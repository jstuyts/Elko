plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":Actor"))
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":ServerCore"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Timer"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
