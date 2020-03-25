plugins {
    java
}

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
    implementation(project(":Server:Workshop"))
    implementation(project(":Trace"))
}

val runWorkshopDev by tasks.registering(JavaExec::class) {
    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.boot.Boot"
    args = mutableListOf(
            "trace_repo=EVENT",
            "trace_comm=EVENT",
            "tracelog_tag=repolog",
            "tracelog_dir=./logs",
            "conf.repository.service=contextdb",
            "conf.repository.name=Workshop",
            "conf.repository.odb=odb-test",
            "conf.listen.host=elkoserver.org:9030",
            "conf.listen.bind=elkoserver.org:9030",
            "conf.listen.protocol=tcp",
            "conf.listen.auth.mode=open",
            "conf.listen.allow=any",
            "conf.listen1.host=elkoserver.org:9031",
            "conf.listen1.bind=elkoserver.org:9031",
            "conf.listen1.protocol=tcp",
            "conf.listen1.auth.mode=password",
            "conf.listen1.auth.code=secret",
            "conf.listen1.allow=admin",
            "conf.broker.host=elkoserver.org:9010",
            "conf.msgdiagnostics=true",
            "org.elkoserver.server.repository.RepositoryBoot"
    )
}
