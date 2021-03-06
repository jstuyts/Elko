plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

dependencies {
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Server:Gatekeeper"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val startGatekeeperDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=gatekeeper",
            "gorgel.system.identifier=dev",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

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

            "conf.gatekeeper.director.host=127.0.0.1:9060",

            "conf.gatekeeper.odjdb=mongo",
            "conf.gatekeeper.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.gatekeeper.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "conf.gatekeeper.name=Gatekeeper",
            "conf.broker.host=127.0.0.1:9010",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.gatekeeper.GatekeeperBoot"
    )
}

val stopGatekeeperDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.GatekeeperShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9031",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
