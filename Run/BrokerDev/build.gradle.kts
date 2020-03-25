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
    implementation(project(":Server:Broker"))
    implementation(project(":Trace"))
}

val runBrokerDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.boot.Boot"
    args = mutableListOf(
            "trace_brok=EVENT",
            "trace_comm=EVENT",
            "tracelog_showverbose=true",
            "tracelog_tag=brklog",
            "tracelog_dir=./logs",
            "conf.broker.name=Broker",
            "conf.listen.host=elkoserver.org:9010",
            "conf.listen.bind=elkoserver.org:9010",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",
            "conf.listen1.host=elkoserver.org:9011",
            "conf.listen1.bind=elkoserver.org:9011",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=admin",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.broker.BrokerBoot"
    )
}
