plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

dependencies {
    implementation(project(":ObjectDatabase:FileObjectStore"))
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Server:Broker"))
    implementation(project(":Server:Context"))
    implementation(project(":Server:Director"))
    implementation(project(":Server:Presence"))
    implementation(project(":Server:Workshop"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
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
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=broker",
            "gorgel.system.identifier=full-production",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.broker.name=broker",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9010",
            "conf.listen.bind=127.0.0.1:9010",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=client",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9011",
            "conf.listen1.bind=127.0.0.1:9011",
            "conf.listen1.protocol=tcp",
            "conf.listen1.allow=any",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",

            "conf.listen2.host=127.0.0.1:9012",
            "conf.listen2.bind=127.0.0.1:9012",
            "conf.listen2.protocol=http",
            "conf.listen2.root=admin",
            "conf.listen2.domain=localhost",
            "conf.listen2.auth.mode=password",
            "conf.listen2.auth.code=figleaf",

            "conf.comm.httptimeout=180",
            "conf.comm.httpselectwait=30",

            // Always use "recover" for now as the launcher table is empty,
            // so "initial" won't have an effect anyway.
            "conf.broker.startmode=recover",

            "conf.broker.odjdb=${File(brokerDataDirectory.get().temporaryDir, "odb").path}",
            "conf.broker.objstore=org.elkoserver.objectdatabase.store.filestore.FileObjectStore",

            "org.elkoserver.server.broker.BrokerBoot"
    )
}

val stopFullProductionBroker by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.BrokerShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val startFullProductionContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=context",
            "gorgel.system.identifier=full-production",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.context.name=context",
            "conf.context.shutdownpassword=figleaf",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9000",
            "conf.listen.bind=127.0.0.1:9000",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9001",
            "conf.listen1.bind=127.0.0.1:9001",
            "conf.listen1.protocol=http",
            "conf.listen1.root=dice",
            "conf.listen1.domain=localhost",
            "conf.listen1.auth.mode=reservation",

            "conf.listen2.host=127.0.0.1:9002",
            "conf.listen2.bind=127.0.0.1:9002",
            "conf.listen2.protocol=rtcp",
            "conf.listen2.auth.mode=reservation",

            "conf.register.auto=true",
            "conf.context.entrytimeout=30",
            "conf.context.reservationexpire=300",
            "conf.presence.auto=true",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.context.odjdb=mongo",
            "conf.context.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopFullProductionContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.ServerViaBrokerShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf",
            "context"
    )
    isIgnoreExitValue = true
}

val startFullProductionDirector by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=director",
            "gorgel.system.identifier=full-production",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.director.name=director",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9060",
            "conf.listen.bind=127.0.0.1:9060",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=user",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9061",
            "conf.listen1.bind=127.0.0.1:9061",
            "conf.listen1.protocol=rtcp",
            "conf.listen1.allow=user",
            "conf.listen1.auth.mode=open",

            "conf.listen2.host=127.0.0.1:9062",
            "conf.listen2.bind=127.0.0.1:9062",
            "conf.listen2.protocol=tcp",
            "conf.listen2.allow=any",
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
    mainClass.set("org.elkoserver.foundation.servermanagement.DirectorShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9062",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val startFullProductionPresence by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=presence",
            "gorgel.system.identifier=full-production",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.presence.name=presence",
            "conf.msgdiagnostics=true",

            "conf.listen.host=127.0.0.1:9040",
            "conf.listen.bind=127.0.0.1:9040",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=any",
            "conf.listen.auth.mode=open",

            "conf.listen1.host=127.0.0.1:9041",
            "conf.listen1.bind=127.0.0.1:9041",
            "conf.listen1.protocol=tcp",
            "conf.listen1.allow=admin",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.presence.odjdb=mongo",
            "conf.presence.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.presence.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.presence.PresenceServerBoot"
    )
}

val stopFullProductionPresence by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.PresenceShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9041",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val startFullProductionWorkshop by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=workshop",
            "gorgel.system.identifier=full-production",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.workshop.name=workshop",
            "conf.msgdiagnostics=true",
            "conf.load.time=300",

            "conf.listen.host=127.0.0.1:9020",
            "conf.listen.bind=127.0.0.1:9020",
            "conf.listen.protocol=tcp",
            "conf.listen.allow=any",
            "conf.listen.auth.mode=password",
            "conf.listen.auth.code=figleaf",

            "conf.workshop.classdesc=classes-bank",

            "conf.broker.host=127.0.0.1:9011",
            "conf.broker.auth.mode=password",
            "conf.broker.auth.code=figleaf",

            "conf.workshop.odjdb=mongo",
            "conf.workshop.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.workshop.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "org.elkoserver.server.workshop.WorkshopBoot"
    )
}

val stopFullProductionWorkshop by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.WorkshopShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9020",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
