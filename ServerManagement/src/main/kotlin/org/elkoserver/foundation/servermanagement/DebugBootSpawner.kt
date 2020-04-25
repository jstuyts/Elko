package org.elkoserver.foundation.servermanagement

/**
 * As Gradle cannot start processes in the background, and a plug-in that was tried could not be made to work, this
 * class was introduced. It simply starts a JVM (without console) that will run `Boot`, and exits. By using
 * this class in Gradle, the build will not block.
 *
 * For this to work, not only the JAR containing this class, but all JARS that would normally be on the class path for
 * a server, have to be on the class path of the JVM running this class.
 */
object DebugBootSpawner {
    @JvmStatic
    fun main(arguments: Array<String>) {
        BootSpawner.spawn(listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"), arguments)
    }
}
