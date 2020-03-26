package org.elkoserver.foundation.servermanagement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * As Gradle cannot start processes in the background, and a plug-in that was tried could not be made to work, this
 * class was introduced. It simply starts a JVM (without console) that will run <code>Boot</code>, and exits. By using
 * this class in Gradle, the build will not block.
 * </p>
 *
 * <p>
 * For this to work, not only the JAR containing this class, but all JARS that would normally be on the class path for
 * a server, hhave to be on the class path of the JVM running this class.
 * </p>
 */
public class BootSpawner {
    public static void main(String[] arguments) throws IOException {
        List<String> commandWithArguments = new ArrayList<>(5 + arguments.length);
        commandWithArguments.add("javaw");
        commandWithArguments.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
        commandWithArguments.add("-cp");
        commandWithArguments.add(System.getProperty("java.class.path"));
        commandWithArguments.add("org.elkoserver.foundation.boot.Boot");
        Collections.addAll(commandWithArguments, arguments);

        new ProcessBuilder(commandWithArguments)
                .directory(new File(System.getProperty("user.dir")))
                .start();
    }
}
