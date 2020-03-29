plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
}

dependencies {
    implementation(project(":Actor"))
    implementation(project(":Boot"))
    implementation(project(":ByteIoFramer:Api"))
    implementation(project(":ByteIoFramer:Json"))
    implementation(project(":Communication"))
    implementation(project(":Json"))
    implementation(project(":JsonMessageHandling"))
    implementation(project(":Net:Api"))
    implementation(project(":ObjectDatabase:Api"))
    implementation(project(":ObjectDatabase:Local"))
    implementation(project(":ObjectDatabase:Remote"))
    implementation(project(":Running"))
    implementation(project(":ScalableSsl"))
    implementation(project(":ServerMetadata"))
    implementation(project(":Timer"))
    implementation(project(":Trace"))
    implementation(project(":Util"))
    implementation("commons-codec:commons-codec:1.9")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
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
