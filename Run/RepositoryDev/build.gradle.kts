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
    implementation(project(":Server:Repository"))
    implementation(project(":ServerManagement"))
    implementation(project(":Trace"))
}

val startRepositoryDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.BootSpawner"
    args = mutableListOf(
            "trace_repo=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=repolog",
            "tracelog_dir=./logs",

            "conf.rep.service=contextdb",
            "conf.rep.name=Repository",
            "conf.rep.odb=odb-test",

            "conf.listen.host=127.0.0.1:9050",
            "conf.listen.bind=127.0.0.1:9050",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",

            "conf.listen1.host=127.0.0.1:9051",
            "conf.listen1.bind=127.0.0.1:9051",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=admin",

            "conf.broker.host=127.0.0.1:9010",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.repository.RepositoryBoot"
    )
}

val stopRepositoryDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.RepositoryShutdown"
    args = mutableListOf(
            "127.0.0.1",
            "9051",
            "secret"
    )
}
