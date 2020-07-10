plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

repositories {
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://dl.bintray.com/squins/Squins")
    }
}

dependencies {
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Server:Presence"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val startPresenceDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "gorgel.system.type=presence",
            "gorgel.system.identifier=dev",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",
            "trace_pres=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=prelog",
            "tracelog_dir=./logs",

            "conf.presence.name=PresenceServer",
            "conf.listen.host=127.0.0.1:9040",
            "conf.listen.bind=127.0.0.1:9040",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=127.0.0.1:9041",
            "conf.listen1.bind=127.0.0.1:9041",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=admin",

            "conf.presence.odjdb=mongo",
            "conf.presence.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.presence.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.presence.PresenceServerBoot"
    )
}

val stopPresenceDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.PresenceShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9041",
            "secret"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
