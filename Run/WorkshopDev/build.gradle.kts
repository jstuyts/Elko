plugins {
    java
}

val mongodbHostAndPort: String? by project
val actualMongodbHostAndPort = mongodbHostAndPort ?: "localhost:27017"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":Boot"))
    implementation(project(":Json"))
    implementation(project(":MongoObjectStore"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":Server:Workshop"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val startWorkshopDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "trace_work=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=work",
            "tracelog_dir=./logs",

            "conf.listen.host=127.0.0.1:9020",
            "conf.listen.bind=127.0.0.1:9020",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=127.0.0.1:9021",
            "conf.listen1.bind=127.0.0.1:9021",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=any",

            "conf.broker.host=127.0.0.1:9011",

            "conf.workshop.odb=mongo",
            "conf.workshop.odb.mongo.hostport=$actualMongodbHostAndPort",
            "conf.workshop.objstore=org.elkoserver.objdb.store.mongostore.MongoObjectStore",
            "conf.workshop.classdesc=\"classes-workshoptest,classes-bank\"",
            "conf.workshop.shutdownpassword=figleaf",
            "conf.workshop.name=Workshop",
            "conf.load.time=300",

            "conf.msgdiagnostics=true",
            "org.elkoserver.server.workshop.WorkshopBoot"
    )
}

val stopWorkshopDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.WorkshopShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9021",
            "secret"
    )
}
