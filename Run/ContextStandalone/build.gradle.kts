plugins {
    java
}

val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":MongoObjectStore"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerManagement"))
}

val startContextStandalone by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_cont=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=context",
            "tracelog_dir=./logs",

            "conf.comm.httptimeout=180",
            "conf.comm.httpselectwait=30",
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

            "conf.context.odb=mongo",
            "conf.context.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.context.classdesc=classes-test",
            "conf.context.shutdownpassword=figleaf",
            "conf.context.name=ContextServer",
            "conf.msgdiagnostics=true",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopContextStandalone by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.ContextShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9000",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
