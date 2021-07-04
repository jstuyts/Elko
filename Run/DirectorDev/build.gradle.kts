plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

dependencies {
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Server:Director"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val startDirectorDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=director",
            "gorgel.system.identifier=dev",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

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
    mainClass.set("org.elkoserver.foundation.servermanagement.DirectorShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9062",
            "-"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
