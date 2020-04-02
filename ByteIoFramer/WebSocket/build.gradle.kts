plugins {
    java
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Http"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Json"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
    implementation(Libraries.commons_codec)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}
