package org.elkoserver.foundation.boot

import org.elkoserver.util.trace.TraceController
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.acceptor.file.TraceLog
import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter
import org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.trace.TraceExceptionNoticer
import java.lang.reflect.InvocationTargetException
import java.time.Clock

/**
 * This is the universal startup class for applications using Elko.  It
 * performs the necessary initializations that all applications require, then
 * launches the application itself by instantiating an instance of the
 * application boot class and calling its [boot()][Bootable.boot]
 * method.
 *
 * The name of the application boot class should be the second argument on the
 * Java command line, according to the following usage:
 * <pre>
 * java org.elkoserver.foundation.boot.Boot *bootclass args...*
 * </pre>
 *
 * The boot class must implement [Bootable].
 *
 * In addition to regular application command line parameters, property
 * settings may also be given on the command line. Arguments that
 * set property values are removed from the arguments array before it is
 * presented to the application.
 *
 * @param myExceptionReporter The exception manager
 * @param bootArguments Command line arguments per Java language spec
 */
private class Boot private constructor(private val myExceptionReporter: ExceptionReporter, private val bootArguments: BootArguments, private val traceFactory: TraceFactory) : Runnable {

    /**
     * The run method mandated by the [Thread] class.  This method is
     * required to be public by Java's scoping rules, but you should not call
     * it yourself.
     *
     * Java's rules don't allow you to declare run() to throw any
     * exceptions, so this just wraps a call to the private method
     * startApplication() in a try/catch block.  The guts of startup are in
     * another method for improved legibility.
     */
    override fun run() {
        try {
            startApplication()
        } catch (e: Exception) {
            myExceptionReporter.reportException(e, "Failure in application startup")
        }
    }

    /**
     * This is the actual server boot thread initialization.
     *
     * When Boot is the first class on the java command line, main() gets
     * called with the remaining command line arguments.  The first such
     * remaining command line argument (myArgs[0]) should be the name of the
     * start class.
     *
     * @throws ClassNotFoundException when myArgs[0] does not correspond to
     * a class on the CLASSPATH.
     *
     * @throws IllegalAccessException when myArgs[0] names a non-permitted
     * class, or a class whose zero-argument constructor is not permitted.
     *
     * @throws InstantiationException when myArgs[0] names an uninstantiatable
     * class, such as an interface or an abstract class.
     */
    private fun startApplication() {
        /* Make an instance of the start class */
        val starter: Bootable
        starter = try {
            Class.forName(bootArguments.mainClassName).getConstructor().newInstance() as Bootable
        } catch (e: ClassCastException) {
            throw ClassCastException(bootArguments.mainClassName + " isn't a Bootable")
        } catch (e: NoSuchMethodException) {
            throw IllegalStateException("Class does not have a public no-arg constructor", e)
        } catch (e: InvocationTargetException) {
            throw IllegalStateException("Error occurred during construction of class", e.cause)
        }
        starter.boot(bootArguments.bootProperties, traceFactory, Clock.systemDefaultZone())
    }

    companion object {
        /**
         * Create a <tt>Boot</tt> object and start it running.
         *
         * @param arguments  Command line arguments per the Java language spec.
         */
        @JvmStatic
        fun main(vararg arguments: String) {
            try {
                tryToBoot(arguments)
            } catch (e: Exception) {
                /* All purpose top-level exception interception */
                e.printStackTrace()
            }
        }

        private fun tryToBoot(arguments: Array<out String>) {
            val bootArguments = BootArguments(*arguments)
            val clock = Clock.systemDefaultZone()
            val traceFactory = createTraceFactory(bootArguments, clock)
            val exceptionReporter = ExceptionReporter(TraceExceptionNoticer(traceFactory.exception))
            val threadGroup = EMThreadGroup("Elko Thread Group", exceptionReporter)
            val boot = Boot(exceptionReporter, bootArguments, traceFactory)
            Thread(threadGroup, boot, "Elko Server Boot").start()
        }

        private fun createTraceFactory(bootArguments: BootArguments, clock: Clock): TraceFactory {
            val traceController = TraceController(TraceLog(clock), clock)
            traceController.start(bootArguments.bootProperties)
            return traceController.factory
        }
    }
}
