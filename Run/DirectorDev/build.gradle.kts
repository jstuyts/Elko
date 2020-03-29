plugins {
    java
}

val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":MongoObjectStore"))
    implementation(project(":Server:Director"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val startDirectorDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_dire=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=dire",
            "tracelog_dir=./logs",

            "conf.listen.host=127.0.0.1:9060",
            "conf.listen.bind=127.0.0.1:9060",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=user",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=127.0.0.1:9061",
            "conf.listen1.bind=127.0.0.1:9061",
            "conf.listen1.auth.mode=open",
            "conf.listen1.allow=user",
            "conf.listen1.protocol=rtcp",

            "conf.listen2.host=127.0.0.1:9062",
            "conf.listen2.bind=127.0.0.1:9062",
            "conf.listen2.auth.mode=open",
            "conf.listen2.allow=any",
            "conf.listen2.protocol=tcp",

            "conf.director.name=Director",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.director.DirectorBoot"
    )
}

val stopDirectorDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DirectorShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9062"
    )
}
