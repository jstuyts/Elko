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
    implementation(project(":Server:Context"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val startContextDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_cont=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=contlog",
            "tracelog_dir=./logs",

            "conf.comm.httptimeout=15",
            "conf.comm.httpselectwait=60",
            "conf.comm.rtcptimeout=300",
            "conf.comm.rtcpdisconntimeout=60",
            "conf.comm.rtcpbacklog=50000",
            "conf.context.entrytimeout=300",
            "conf.comm.jsonstrictness=true",

            "conf.listen.host=127.0.0.1:8000",
            "conf.listen.bind=127.0.0.1:8000",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=127.0.0.1:8001",
            "conf.listen1.bind=127.0.0.1:8001",
            "conf.listen1.protocol=http",
            "conf.listen1.domain=localhost",
            "conf.listen1.root=test",

            "conf.listen2.host=127.0.0.1:8002",
            "conf.listen2.bind=127.0.0.1:8002",
            "conf.listen2.protocol=rtcp",

            "conf.listen3.host=127.0.0.1:8003",
            "conf.listen3.bind=127.0.0.1:8003",
            "conf.listen3.protocol=tcp",
            "conf.listen3.allow=internal",

            "conf.listen4.host=127.0.0.1:8004",
            "conf.listen4.bind=127.0.0.1:8004",
            "conf.listen4.protocol=ws",
            "conf.listen4.sock=test",

            "conf.context.odb=mongo",
            "conf.context.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.context.classdesc=\"classes-test,classes-bank,classes-app\"",
            "conf.context.shutdownpassword=figleaf",
            "conf.context.name=Context",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopContextDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.ContextShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "8003",
            "figleaf"
    )
}
