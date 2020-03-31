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
    implementation(project(":Boot:Api"))
    implementation(project(":Boot:App"))
    implementation(project(":Json"))
    implementation(project(":FileObjectStore"))
    implementation(project(":MongoObjectStore"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":Server:Broker"))
    implementation(project(":Server:Context"))
    implementation(project(":Server:Director"))
    implementation(project(":Server:Gatekeeper"))
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

val startClusterManagedBroker by tasks.registering(JavaExec::class) {
    group = "Elko"

    inputs.files(brokerDataDirectory)

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_brok=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=broker",
            "tracelog_dir=./logs",

            "conf.broker.name=Broker",

            "conf.listen.host=127.0.0.1:9010",
            "conf.listen.bind=127.0.0.1:9010",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=127.0.0.1:9011",
            "conf.listen1.bind=127.0.0.1:9011",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",
            "conf.listen1.allow=admin",

            "conf.msgdiagnostics=true",

            "conf.broker.odb=${File(brokerDataDirectory.get().temporaryDir, "odb").path}",

            "org.elkoserver.server.broker.BrokerBoot"
    )
}

val stopClusterManagedBroker by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BrokerShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf"
    )
}

val startClusterManagedContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_cont=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=ContextServer",
            "tracelog_dir=./logs",

            "conf.listen.host=127.0.0.1:9000",
            "conf.listen.bind=127.0.0.1:9000",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=reservation",

            "conf.listen1.host=127.0.0.1:9001",
            "conf.listen1.bind=127.0.0.1:9001",
            "conf.listen1.protocol=http",
            "conf.listen1.domain=localhost",
            "conf.listen1.root=test",
            "conf.listen1.auth.mode=reservation",

            "conf.register.auto=true",
            "conf.broker.host=127.0.0.1:9010",

            "conf.context.entrytimeout=300",

            "conf.context.odb=mongo",
            "conf.context.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.context.name=ContextServer",
            "conf.context.shutdownpassword=figleaf",
            "conf.msgdiagnostics=true",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopClusterManagedContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.ServerViaBrokerShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "figleaf",
            "ContextServer"
    )
}

val startClusterManagedDirector by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_dire=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=director",
            "tracelog_dir=./logs",

            "conf.listen.host=127.0.0.1:9060",
            "conf.listen.bind=127.0.0.1:9060",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=user",

            "conf.listen1.host=127.0.0.1:9061",
            "conf.listen1.bind=127.0.0.1:9061",
            "conf.listen1.auth.mode=open",
            "conf.listen1.allow=any",

            "conf.broker.host=127.0.0.1:9011",

            "conf.director.name=Director",
            "conf.msgdiagnostics=true",

            "org.elkoserver.server.director.DirectorBoot"
    )
}

val stopClusterManagedDirector by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DirectorShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9061"
    )
}

val startClusterManagedGatekeeper by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_gate=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=gatekeeper",
            "tracelog_dir=./logs",

            "conf.listen.host=127.0.0.1:9030",
            "conf.listen.bind=127.0.0.1:9030",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=127.0.0.1:9031",
            "conf.listen1.bind=127.0.0.1:9031",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",
            "conf.listen1.allow=admin",

            "conf.register.auto=true",
            "conf.broker.host=127.0.0.1:9011",

            "conf.gatekeeper.director.host=127.0.0.1:9060",

            "conf.gatekeeper.director.auto=true",
            "conf.gatekeeper.name=Gatekeeper",

            "conf.gatekeeper.odb=mongo",
            "conf.gatekeeper.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.gatekeeper.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.msgdiagnostics=true",

            "org.elkoserver.server.gatekeeper.GatekeeperBoot"
    )
}

val stopClusterManagedGatekeeper by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.GatekeeperShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9031",
            "figleaf"
    )
}
