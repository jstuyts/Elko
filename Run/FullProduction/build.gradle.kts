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
    implementation(project(":FileObjectStore"))
    implementation(project(":MongoObjectStore"))
    implementation(project(":Server:Broker"))
    implementation(project(":Server:Context"))
    implementation(project(":Server:Director"))
    implementation(project(":Server:Presence"))
    implementation(project(":Server:Workshop"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val brokerDataDirectory by tasks.registering {
    group = "Elko"

    val upToDateMarkerFile = File(temporaryDir, "upToDateMarker.txt")

    inputs.files("odb/*.json")
    outputs.file(upToDateMarkerFile)

    doLast {
        upToDateMarkerFile.writeText("")

        val odbDirectory = File(temporaryDir, "odb")
        mkdir(odbDirectory)
        copy {
            from("odb")
            into(odbDirectory)
            include("*.json")
        }
    }
}

val startFullProductionBroker by tasks.registering(JavaExec::class) {
    group = "Elko"

    inputs.files(brokerDataDirectory)

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_brok=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=brok",
            "tracelog_dir=./logs",
            "tracelog_rollover=daily",

            "conf.broker.name=broker",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9010",
            "conf.listen.bind=127.0.0.1:9010",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=client",
            "conf.listen.secure=",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9011",
            "conf.listen1.bind=127.0.0.1:9011",
            "conf.listen1.protocol=tcp",
            "conf.listen1.allow=any",
            "conf.listen1.secure=",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",

            "conf.listen2.host=127.0.0.1:9012",
            "conf.listen2.bind=127.0.0.1:9012",
            "conf.listen2.protocol=http",
            "conf.listen2.secure=",
            "conf.listen2.root=admin",
            "conf.listen2.domain=localhost",
            "conf.listen2.auth.mode=password",
            "conf.listen2.auth.code=figleaf",

            "conf.comm.httptimeout=180",
            "conf.comm.httpselectwait=30",

            // Alwyas use "recover" for now as the launcher table is empty,
            // so "initial" won't have an effect anyway.
            "conf.broker.startmode=recover",

            "conf.broker.odb=${File(brokerDataDirectory.get().temporaryDir, "odb").path}",
            "conf.broker.objstore=org.elkoserver.objdb.store.filestore.FileObjectStore",

            "org.elkoserver.server.broker.BrokerBoot"
    )
}

val stopFullProductionBroker by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BrokerShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf"
    )
}

val startFullProductionContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_cont=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=cont",
            "tracelog_dir=./logs",
            "tracelog_rollover=daily",

            "conf.context.name=context",
            "conf.context.shutdownpassword=figleaf",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9000",
            "conf.listen.bind=127.0.0.1:9000",
            "conf.listen.protocol=tcp",
            "conf.listen.secure=",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9001",
            "conf.listen1.bind=127.0.0.1:9001",
            "conf.listen1.protocol=http",
            "conf.listen1.secure=",
            "conf.listen1.root=dice",
            "conf.listen1.domain=localhost",
            "conf.listen1.auth.mode=reservation",

            "conf.listen2.host=127.0.0.1:9002",
            "conf.listen2.bind=127.0.0.1:9002",
            "conf.listen2.protocol=rtcp",
            "conf.listen2.secure=",
            "conf.listen2.auth.mode=reservation",

            "conf.register.auto=true",
            "conf.context.entrytimeout=30",
            "conf.context.reservationexpire=300",
            "conf.presence.auto=true",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.context.odb=mongo",
            "conf.context.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopFullProductionContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.ServerViaBrokerShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf",
            "context"
    )
}

val startFullProductionDirector by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_dire=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=dire",
            "tracelog_dir=./logs",
            "tracelog_rollover=daily",

            "conf.director.name=director",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9060",
            "conf.listen.bind=127.0.0.1:9060",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=user",
            "conf.listen.secure=",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9061",
            "conf.listen1.bind=127.0.0.1:9061",
            "conf.listen1.protocol=rtcp",
            "conf.listen1.allow=user",
            "conf.listen1.secure=",
            "conf.listen1.auth.mode=open",

            "conf.listen2.host=127.0.0.1:9062",
            "conf.listen2.bind=127.0.0.1:9062",
            "conf.listen2.protocol=tcp",
            "conf.listen2.allow=any",
            "conf.listen2.secure=",
            "conf.listen2.auth.mode=password",
            "conf.listen2.auth.code=figleaf",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "org.elkoserver.server.director.DirectorBoot"
    )
}

val stopFullProductionDirector by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DirectorShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9062",
            "figleaf"
    )
}

val startFullProductionPresence by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_pres=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=pres",
            "tracelog_dir=./logs",
            "tracelog_rollover=daily",

            "conf.presence.name=presence",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9040",
            "conf.listen.bind=127.0.0.1:9040",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=any",
            "conf.listen.secure=",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9041",
            "conf.listen1.bind=127.0.0.1:9041",
            "conf.listen1.protocol=tcp",
            "conf.listen1.allow=admin",
            "conf.listen1.secure=",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.presence.odb=mongo",
            "conf.presence.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.presence.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.presence.PresenceServerBoot"
    )
}

val stopFullProductionPresence by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.PresenceShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9041",
            "figleaf"
    )
}

val startFullProductionWorkshop by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_work=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=work",
            "tracelog_dir=./logs",
            "tracelog_rollover=daily",

            "conf.workshop.name=workshop",
            "conf.msgdiagnostics=true",
            "conf.load.time=300",

            "conf.listen.host=127.0.0.1:9020",
            "conf.listen.bind=127.0.0.1:9020",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=any",
            "conf.listen.secure=",
            "conf.listen.auth.mode=password",
            "conf.listen.auth.code=figleaf",

            "conf.workshop.classdesc=classes-bank",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.workshop.odb=mongo",
            "conf.workshop.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.workshop.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "org.elkoserver.server.workshop.WorkshopBoot"
    )
}

val stopFullProductionWorkshop by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.WorkshopShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9020",
            "figleaf"
    )
}