package org.elkoserver.foundation.servermanagement;

import java.io.IOException;

import static java.util.Collections.singletonList;
import static org.elkoserver.foundation.servermanagement.BootSpawner.spawn;

/**
 * <p>
 * As Gradle cannot start processes in the background, and a plug-in that was tried could not be made to work, this
 * class was introduced. It simply starts a JVM (without console) that will run <code>Boot</code>, and exits. By using
 * this class in Gradle, the build will not block.
 * </p>
 *
 * <p>
 * For this to work, not only the JAR containing this class, but all JARS that would normally be on the class path for
 * a server, have to be on the class path of the JVM running this class.
 * </p>
 */
public class DebugBootSpawner {
    public static void main(String[] arguments) throws IOException {
        spawn(singletonList("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"), arguments);
    }
}
