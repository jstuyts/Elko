package org.elkoserver.foundation.boot;

import org.elkoserver.util.trace.TraceController;
import org.elkoserver.util.trace.TraceFactory;
import org.elkoserver.util.trace.acceptor.file.TraceLog;
import org.elkoserver.util.trace.exceptionreporting.ExceptionReporter;
import org.elkoserver.util.trace.exceptionreporting.exceptionnoticer.trace.TraceExceptionNoticer;

import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * This is the universal startup class for applications using Elko.  It
 * performs the necessary initializations that all applications require, then
 * launches the application itself by instantiating an instance of the
 * application boot class and calling its {@link Bootable#boot boot()}
 * method.<p>
 *
 * The name of the application boot class should be the second argument on the
 * Java command line, according to the following usage: <pre>
 *     java org.elkoserver.foundation.boot.Boot <em>bootclass args...</em>
 * </pre>
 *
 * The boot class must implement {@link Bootable}.<p>
 *
 * In addition to regular application command line parameters, property
 * settings may also be given on the command line. Arguments that
 * set property values are removed from the arguments array before it is
 * presented to the application.<p>
 */
class Boot implements Runnable {

    private final ExceptionReporter myExceptionReporter;
    private final BootArguments bootArguments;
    private final TraceFactory traceFactory;

    /**
     * Create the thread from which everything else will unfold.
     * @param exceptionReporter The exception manager
     * @param bootArguments Command line arguments per Java language spec
     */
    private Boot(ExceptionReporter exceptionReporter, BootArguments bootArguments, TraceFactory traceFactory) {
        super();

        myExceptionReporter = exceptionReporter;
        this.bootArguments = bootArguments;
        this.traceFactory = traceFactory;
    }

    /**
     * Create a <tt>Boot</tt> object and start it running.
     *
     * @param args  Command line arguments per the Java language spec.
     */
    public static void main(String[] args) {
        try {
            BootArguments bootArguments = new BootArguments(args);
            Clock clock = Clock.systemDefaultZone();
            TraceController traceController = new TraceController(new TraceLog(clock), clock);
            traceController.start(bootArguments.bootProperties);
            ExceptionReporter exceptionReporter = new ExceptionReporter(new TraceExceptionNoticer(traceController.getFactory().getException()));
            EMThreadGroup threadGroup = new EMThreadGroup("Elko Thread Group", exceptionReporter);
            Boot boot = new Boot(exceptionReporter, bootArguments, traceController.getFactory());
            new Thread(threadGroup, boot, "Elko Server Boot").start();
        } catch (Exception e) {
            /* All purpose top-level exception interception */
            e.printStackTrace();
        }
    }

    /**
     * Thread group for all server application threads to run in.  Will punt
     * all uncaught exceptions to the {@link ExceptionReporter} class.
     */
    private static class EMThreadGroup extends ThreadGroup {

        private ExceptionReporter myExceptionReporter;

        /**
         * Standard thread group constructor.
         *
         * @param name The name for the new thread group.
         */
        EMThreadGroup(String name, ExceptionReporter exceptionReporter) {
            super(name);
            myExceptionReporter = exceptionReporter;
        }
        
        /**
         * Handle uncaught exceptions by giving them to the
         * {@link ExceptionReporter}.
         *
         * @param thread  The thread in which the exception was thrown (and not
         *    caught).
         * @param ex  The exception itself.
         */
        public void uncaughtException(Thread thread, Throwable ex) {
            myExceptionReporter.uncaughtException(thread, ex);
        }
    }

    /**
     * The run method mandated by the {@link Thread} class.  This method is
     * required to be public by Java's scoping rules, but you should not call
     * it yourself.
     *
     * Java's rules don't allow you to declare run() to throw any
     * exceptions, so this just wraps a call to the private method
     * startApplication() in a try/catch block.  The guts of startup are in
     * another method for improved legibility.
     */
    public void run() {
        try {
            startApplication();
        } catch (Exception e) {
            myExceptionReporter.reportException(e,
                                             "Failure in application startup");
        }
    }

    /**
     * This is the actual server boot thread initialization.<p>
     *
     * When Boot is the first class on the java command line, main() gets
     * called with the remaining command line arguments.  The first such
     * remaining command line argument (myArgs[0]) should be the name of the
     * start class.
     *
     * @throws ClassNotFoundException when myArgs[0] does not correspond to
     *   a class on the CLASSPATH.
     *
     * @throws IllegalAccessException when myArgs[0] names a non-permitted
     *   class, or a class whose zero-argument constructor is not permitted.
     *
     * @throws InstantiationException when myArgs[0] names an uninstantiatable
     *   class, such as an interface or an abstract class.
     */
    private void startApplication() throws ClassNotFoundException,
            IllegalAccessException, InstantiationException
    {
        /* Make an instance of the start class */
        Bootable starter;
        try {
            starter = (Bootable) Class.forName(bootArguments.mainClassName).getConstructor().newInstance();
        } catch (ClassCastException e) {
            throw new ClassCastException(bootArguments.mainClassName + " isn't a Bootable");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Class does not have a public no-arg constructor", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Error occurred during construction of class", e.getCause());
        }

        starter.boot(bootArguments.bootProperties, traceFactory);
    }
}
