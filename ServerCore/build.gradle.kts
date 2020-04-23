import org.elkoserver.develop.gradle.apiclasses.ApiClassesTask

plugins {
    `java-library`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    api(project(":Actor"))
    api(project(":JsonMessageHandling"))
    api(project(":Net:Api"))
    api(project(":ObjectDatabase:Api"))
    api(project(":Properties"))
    api(project(":ServerMetadata"))
    api(project(":Timer"))
    api(project(":Trace"))
    api(Libraries.nanojson)

    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Communication"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":ObjectDatabase:Remote"))
    implementation(project(":Running"))
    implementation(project(":Util"))
}

val apiClasses by tasks.registering(ApiClassesTask::class) {
    dependsOn(tasks.classes)
}

java {
    sourceCompatibility = JavaVersion.VERSION_12
}

val generateBuildVersionClassSourceFile by tasks.registering {
    inputs.property("version", project.version)
    outputs.dir(temporaryDir)

    doLast {
        val packageDirectory = File(temporaryDir, "org/elkoserver/foundation/server")
        packageDirectory.mkdirs()
        val buildVersionClassSourceFile = File(packageDirectory, "BuildVersion.java")
        buildVersionClassSourceFile.writeText("""package org.elkoserver.foundation.server;

class BuildVersion {
    static public String version = "${project.version}";
}
""")
    }
}

tasks.withType<JavaCompile>().forEach { task -> task.setDependsOn(listOf(generateBuildVersionClassSourceFile)) }

sourceSets {
    main {
        java {
            this.srcDir(generateBuildVersionClassSourceFile)
        }
    }
}

/* Groovy to port:
task sourcesJar(type: Jar) {
    classifier = 'sources'
    from(sourceSets.main.allJava)
}

publishing {
    publications {
        maven(MavenPublication) {
            from(components.java)
            artifact(sourcesJar)
        }
    }
}
*/
