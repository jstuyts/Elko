plugins {
    java
}

val logbackRunConfigurationFilePath: String by project(":Run").extra

dependencies {
    implementation(project(":Feature:BasicExamples"))
    implementation(project(":Feature:Capabilities"))
    implementation(project(":ObjectDatabase:FileObjectStore"))
    implementation(project(":Server:Repository"))
    implementation(project(":ServerManagement"))
    implementation(Libraries.logstash_logback_encoder)
}

val repositoryDataDirectory by tasks.registering {
    group = "Elko"

    val upToDateMarkerFile = File(temporaryDir, "upToDateMarker.txt")

    inputs.files("odb/*.json")
    outputs.file(upToDateMarkerFile)

    doLast {
        upToDateMarkerFile.writeText("")

        val odbDirectory = File(temporaryDir, "odb")
        mkdir(odbDirectory)
        copy {
            from("odb")
            into(odbDirectory)
            include("*.json")
        }
    }
}

val startRepositoryDev by tasks.registering(JavaExec::class) {
    group = "Elko"

    inputs.files(repositoryDataDirectory)

    classpath = sourceSets["main"].runtimeClasspath
    main = "org.elkoserver.foundation.servermanagement.DebugBootSpawner"
    args = mutableListOf(
            "gorgel.system.type=repository",
            "gorgel.system.identifier=dev",
            "gorgel.configuration.file=$logbackRunConfigurationFilePath",

            "conf.rep.service=contextdb",
            "conf.rep.name=Repository",
            "conf.rep.odjdb=${File(repositoryDataDirectory.get().temporaryDir, "odb").path}",

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
    isIgnoreExitValue = true
}

val cleanRunLogs by tasks.registering(Delete::class) {
    delete(fileTree("logs") {
        exclude(".*")
    })
}
