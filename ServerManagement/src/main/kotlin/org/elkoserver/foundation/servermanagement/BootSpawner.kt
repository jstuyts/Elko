package org.elkoserver.foundation.servermanagement

import java.io.File
import java.util.Collections
import kotlin.system.exitProcess

/**
 * As Gradle cannot start processes in the background, and a plug-in that was tried could not be made to work, this
 * class was introduced. It simply starts a JVM (without console) that will run `Boot`, and exits. By using
 * this class in Gradle, the build will not block.
 *
 * For this to work, not only the JAR containing this class, but all JARS that would normally be on the class path for
 * a server, have to be on the class path of the JVM running this class.
 */
object BootSpawner {
    @JvmStatic
    fun main(arguments: Array<String>) {
        spawn(emptyList(), arguments)
    }

    internal fun spawn(additionalJavaOptions: List<String>, arguments: Array<String>) {
        val commandWithArguments: MutableList<String> = ArrayList<String>(4 + additionalJavaOptions.size + arguments.size).apply {
            add("javaw")
            addAll(additionalJavaOptions)
            add("-cp")
            add(System.getProperty("java.class.path"))
            add("org.elkoserver.foundation.boot.Boot")
        }
        Collections.addAll(commandWithArguments, *arguments)
        val bootProcess = ProcessBuilder(commandWithArguments)
                .directory(File(System.getProperty("user.dir")))
                .start()
        try {
            Thread.sleep(1000L)
            if (!bootProcess.isAlive) {
                exitProcess(bootProcess.exitValue())
            }
        } catch (e: InterruptedException) {
            // No action needed. Simply exit this thread.
        }
    }
}
