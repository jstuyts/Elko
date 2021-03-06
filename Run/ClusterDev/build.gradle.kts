plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

dependencies {
    implementation(project(":ObjectDatabase:FileObjectStore"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val startClusterDevContext1 by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=context",
            "gorgel.system.identifier=cluster-dev-1",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

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
            "conf.listen2.protocol=tcp",
            "conf.listen2.auth.mode=reservation",

            "conf.listen3.host=127.0.0.1:9003",
            "conf.listen3.bind=127.0.0.1:9003",
            "conf.listen3.protocol=http",
            "conf.listen3.domain=localhost",
            "conf.listen3.root=test",
            "conf.listen3.auth.mode=reservation",

            "conf.register.host=127.0.0.1:9060",

            "conf.context.odjdb=odb-test",
            "conf.context.classdesc=classes-test,classes-app",

            "conf.context.name=ContextServer1",
            "conf.msgdiagnostics=true",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopClusterDevContext1 by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.ContextShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9000",
            "-"
    )
    isIgnoreExitValue = true
}

val startClusterDevContext2 by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.DebugBootSpawner")
    args = mutableListOf(
            "gorgel.system.type=context",
            "gorgel.system.identifier=cluster-dev-2",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.listen.host=127.0.0.1:9100",
            "conf.listen.bind=127.0.0.1:9100",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=127.0.0.1:9101",
            "conf.listen1.bind=127.0.0.1:9101",
            "conf.listen1.protocol=http",
            "conf.listen1.domain=localhost",
            "conf.listen1.root=test",

            "conf.listen2.host=127.0.0.1:9102",
            "conf.listen2.bind=127.0.0.1:9102",
            "conf.listen2.protocol=tcp",
            "conf.listen2.auth.mode=reservation",

            "conf.listen3.host=127.0.0.1:9103",
            "conf.listen3.bind=127.0.0.1:9103",
            "conf.listen3.protocol=http",
            "conf.listen3.domain=localhost",
            "conf.listen3.root=test",
            "conf.listen3.auth.mode=reservation",

            "conf.register.host=127.0.0.1:9060",

            "conf.context.odjdb=odb-test",
            "conf.context.classdesc=classes-test,classes-app",

            "conf.context.name=ContextServer2",
            "conf.msgdiagnostics=true",

            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopClusterDevContext2 by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.elkoserver.foundation.servermanagement.ContextShutdown")
    args = mutableListOf(
            "127.0.0.1",
            "9100",
            "-"
    )
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
