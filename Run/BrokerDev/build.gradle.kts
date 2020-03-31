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
    implementation(project(":MongoObjectStore"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":Server:Broker"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val startBrokerDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_brok=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=brklog",
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
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=admin",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.broker.BrokerBoot"
    )
}

val stopBrokerDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BrokerShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9011",
            "secret"
    )
}
