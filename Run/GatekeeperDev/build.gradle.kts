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
    implementation(project(":Server:Gatekeeper"))
    implementation(project(":Trace"))
}

val runGatekeeperDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.boot.Boot"
    args = mutableListOf(
            "trace_gate=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=gatelog",
            "tracelog_dir=./logs",

            "conf.listen.host=elkoserver.org:9030",
            "conf.listen.bind=elkoserver.org:9030",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=elkoserver.org:9031",
            "conf.listen1.bind=elkoserver.org:9031",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=figleaf",
            "conf.listen1.allow=admin",

            "conf.gatekeeper.director.host=elkoserver.org:9060",

            "conf.gatekeeper.odb=mongo",
            "conf.gatekeeper.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.gatekeeper.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",

            "conf.gatekeeper.name=Gatekeeper",
            "conf.broker.host=elkoserver.org:9010",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.gatekeeper.GatekeeperBoot"
    )
}
