plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

dependencies {
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val startContextDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=context",
            "gorgel.system.identifier=dev",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.comm.httptimeout=15",
            "conf.comm.httpselectwait=60",
            "conf.comm.rtcptimeout=300",
            "conf.comm.rtcpdisconntimeout=60",
            "conf.comm.rtcpbacklog=50000",
            "conf.context.entrytimeout=300",

            "conf.listen.host=127.0.0.1:9000",
            "conf.listen.bind=127.0.0.1:9000",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=127.0.0.1:9001",
            "conf.listen1.bind=127.0.0.1:9001",
            "conf.listen1.protocol=http",
            "conf.listen1.domain=localhost",
            "conf.listen1.root=test",

            "conf.listen2.host=127.0.0.1:9002",
            "conf.listen2.bind=127.0.0.1:9002",
            "conf.listen2.protocol=rtcp",

            "conf.listen3.host=127.0.0.1:9003",
            "conf.listen3.bind=127.0.0.1:9003",
            "conf.listen3.protocol=tcp",
            "conf.listen3.allow=internal",

            "conf.listen4.host=127.0.0.1:9004",
            "conf.listen4.bind=127.0.0.1:9004",
            "conf.listen4.protocol=ws",
            "conf.listen4.sock=test",

            "conf.context.odjdb=mongo",
            "conf.context.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "conf.context.classdesc=classes-test,classes-bank,classes-app",
            "conf.context.shutdownpassword=figleaf",
            "conf.context.name=Context",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopContextDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.ContextShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9003",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
