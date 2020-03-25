plugins {
    java
}

val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

repositories {
    mavenCentral()
    maven {
        url = uri("http://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":MongoObjectStore"))
    implementation(project(":Server:Director"))
    implementation(project(":Trace"))
}

val runDirectorDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.boot.Boot"
    args = mutableListOf(
            "trace_dire=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=dire",
            "tracelog_dir=./logs",

            "conf.listen.host=elkoserver.org:9060",
            "conf.listen.bind=elkoserver.org:9060",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=user",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=elkoserver.org:9061",
            "conf.listen1.bind=elkoserver.org:9061",
            "conf.listen1.auth.mode=open",
            "conf.listen1.allow=user",
            "conf.listen1.protocol=rtcp",

            "conf.listen2.host=elkoserver.org:9062",
            "conf.listen2.bind=elkoserver.org:9062",
            "conf.listen2.auth.mode=open",
            "conf.listen2.allow=any",
            "conf.listen2.protocol=tcp",

            "conf.director.name=Director",
            "conf.comm.jsonstrictness=true",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.director.DirectorBoot"
    )
}
