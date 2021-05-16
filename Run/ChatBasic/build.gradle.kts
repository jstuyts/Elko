import org.elkoserver.develop.gradle.mongodb.initializeMongodbCollection
import java.net.InetAddress

plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra
val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"
val databaseName = "chatbasic"
val collectionName = "chats"

val webFiles by configurations.creating { }

dependencies {
    implementation(project(":Example"))
    implementation(project(":ObjectDatabase:MongoObjectStore"))
    implementation(project(":Run:services:WebServer"))
    implementation(project(":Server:Context"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)

    webFiles(project(":Presunce:JavaScript")) {
        targetConfiguration = "archives"
    }
}

val initializeChatBasicDatabase by tasks.registering {
    doLast {
        initializeMongodbCollection(actualMongodbHostAndPort, databaseName, collectionName, project.file("odb"))
    }
}

val startChatBasicContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    val hostName = InetAddress.getLocalHost().hostName

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "gorgel.system.type=context",
            "gorgel.system.identifier=chat-basic",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.comm.httptimeout=15",
            "conf.comm.httpselectwait=60",
            "conf.comm.rtcptimeout=300",
            "conf.comm.rtcpdisconntimeout=60",
            "conf.comm.rtcpbacklog=50000",
            "conf.context.entrytimeout=300",

            "conf.listen.host=$hostName:9000",
            "conf.listen.bind=0.0.0.0:9000",
            "conf.listen.protocol=tcp",

            "conf.listen1.host=$hostName:9001",
            "conf.listen1.bind=0.0.0.0:9001",
            "conf.listen1.protocol=http",
            "conf.listen1.domain=localhost",
            "conf.listen1.root=test",

            "conf.listen2.host=$hostName:9002",
            "conf.listen2.bind=0.0.0.0:9002",
            "conf.listen2.protocol=rtcp",

            "conf.listen3.host=$hostName:9003",
            "conf.listen3.bind=0.0.0.0:9003",
            "conf.listen3.protocol=tcp",
            "conf.listen3.allow=internal",

            "conf.listen4.host=$hostName:9004",
            "conf.listen4.bind=0.0.0.0:9004",
            "conf.listen4.protocol=ws",
            "conf.listen4.sock=test",

            "conf.context.odjdb=mongo",
            "conf.context.odjdb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.context.odjdb.mongo.dbname=$databaseName",
            "conf.context.odjdb.mongo.collname=$collectionName",
            "conf.context.objstore=org.elkoserver.objectdatabase.store.mongostore.MongoObjectStore",

            "conf.context.classdesc=classes-chat",
            "conf.context.shutdownpassword=figleaf",
            "conf.context.name=Context",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.context.ContextServerBoot"
    )
}

val stopChatBasicContext by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.ContextShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9003",
            "figleaf"
    )
    isIgnoreExitValue = true
}

val createChatBasicWebRoot by tasks.registering(Copy::class) {
    inputs.files(webFiles)
    from("web")
    from({ webFiles.resolve().map(this@Build_gradle::zipTree) })
    into(temporaryDir)
}

val startChatBasicWebServer by tasks.registering(JavaExec::class) {
    group = "Elko"
    dependsOn(createChatBasicWebRoot)

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.run.services.webserver.StartWebServerKt"
    args = mutableListOf(
            createChatBasicWebRoot.get().temporaryDir.absolutePath,
            "figleaf",
            "--listen",
            "0.0.0.0",
            "--port",
            "8080"
    )
}

val stopChatBasicWebServer by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.run.services.webserver.StopWebServerKt"
    args = mutableListOf(
            "figleaf",
            "--port",
            "8080"
    )
    isIgnoreExitValue = true
}

val startChatBasicAll by tasks.registering {
    dependsOn(startChatBasicContext, startChatBasicWebServer)
}

val stopChatBasicAll by tasks.registering {
    dependsOn(stopChatBasicContext, stopChatBasicWebServer)
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
