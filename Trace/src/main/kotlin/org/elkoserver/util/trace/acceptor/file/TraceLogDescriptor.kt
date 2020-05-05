package org.elkoserver.util.trace.acceptor.file

import org.elkoserver.util.trace.terseCompleteDateString
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.Charset
import java.time.Clock

/**
 * This class describes the file system interface to a log file.  The standard
 * filename format is &lt;tag>.&lt;date>.  The standard versioned filename format is
 * then &lt;tag>.&lt;date>.&lt;sequence>.
 *
 * The entire format may be overridden, the tag may be changed, or the class
 * can be instructed to use System.out.
 *
 * This class is responsible for opening new files.
 */
internal class TraceLogDescriptor(private val clock: Clock) : Cloneable {
    /** The directory in which the log lives.  */
    private var myDir = STARTING_LOG_DIR

    /** The 'tag' is the first component of the log filename.  */
    private var myTag = STARTING_LOG_TAG

    /** Determine whether System.out is used instead of a file.  */
    internal var useStdout = false

    /** True if the user overrode the standard &lt;tag>.&lt;date> format. */
    internal var usePersonalFormat = false

    /** The file being used for the log, if format chosen by user.  */
    private var myPersonalFile: String? = null

    /** The stream open to the log.  Clients print to this.  */
    @JvmField
    var stream: PrintStream? = null

    /** The log file to open on rollover.  */
    private var myNextFile: File? = null

    /** The actual file that got opened.  */
    private var myFile: File? = null

    companion object {
        /*
         * XXX At some point, this might be initialized to some default directory.
         * In Windows, the "current working directory" has a bad habit of hopping
         * around at runtime.
         */
        private val STARTING_LOG_DIR = File(".")
        private const val STARTING_LOG_TAG = "log"
    }

    /**
     * Get the file to use as as the next version of a file.  Stdout is never
     * versioned, so useStdout should be false.
     *
     * @param file  The file that we desire a version name for.
     * @param clashAction  How the versioned name is to be determined.
     * ADD means a file with the next highest sequence number.
     * OVERWRITE means a file with the smallest sequence number.
     */
    private fun versionFile(file: File, clashAction: ClashAction): File {
        return clashAction.versionFile(file)
    }

    /**
     * A clone of a TraceLogDescriptor is one that, when startUsing() is
     * called, will use the same descriptor, be it a file or System.out.  The
     * clone is not inUse(), even if what it was cloned from was.
     */
    public override fun clone(): Any {
        val cl = super.clone() as TraceLogDescriptor
        cl.stream = null
        return cl
    }

    /**
     * Figure out what the next log file name will be when rolling over log
     * files.  This method is used because the current log file will be closed
     * before the next one is opened.  Predetermining the file here enables ust
     * to write some information about the new file into the old one.
     */
    fun prepareToRollover(clashAction: ClashAction) {
        if (useStdout) {
            myNextFile = null
            return
        }
        val newMyNextFile = desiredLogFile()
        if (!newMyNextFile.exists()) {
            myNextFile = newMyNextFile
        } else {
            myNextFile = versionFile(newMyNextFile, clashAction)
        }
    }

    /**
     * Given this object's configuration state, construct the filename the user
     * wants.  It is a program error to call this routine if the user wants
     * System.out, not a file.
     */
    private fun desiredLogFile(): File {
        return if (usePersonalFormat) {
            if (File(myPersonalFile).isAbsolute) {
                File(myPersonalFile)
            } else {
                File(myDir, myPersonalFile)
            }
        } else {
            val timestamp = clock.millis()
            File(myDir, myTag + "." +
                    terseCompleteDateString(timestamp))
        }
    }

    /**
     * Two TraceLogDescriptors are equal iff they refer to the same (canonical)
     * file.
     */
    fun equals(other: TraceLogDescriptor): Boolean {
        return printName() == other.printName()
    }

    /**
     * Return a name of this descriptor, suitable for printing.  System.out is
     * named "standard output".  Real files are named by their canonical
     * pathname (surrounded by single quotes).
     *
     * Note that the printname may be the absolute pathname if the canonical
     * path could not be discovered (which could happen if the file does not
     * exist.)
     */
    fun printName(): String {
        return if (useStdout) {
            "standard output"
        } else if (myFile == null) {
            "unknown"
        } else {
            val canonical: String
            canonical = try {
                myFile!!.canonicalPath
            } catch (e: IOException) {
                /* The canonical path was undiscoverable.  Punt by returning
                   the absolute pathname. */
                myFile!!.absolutePath
            }
            "'$canonical'"
        }
    }

    /**
     * The user wishes to use a directory component different than the default.
     * The file used is unchanged.
     */
    fun setDir(value: String) {
        useStdout = false
        /* Don't change value of usePersonalFormat, as the directory is
           independent of the filename format. */
        myDir = File(value)
        myDir.isDirectory
    }

    /**
     * If the argument is "-", standard output is used.  If the argument is
     * something else, that becomes the complete filename, overriding the tag,
     * eliminating use of the date/time field, and not using the default
     * extension.  It does not affect the directory the file is placed in.
     */
    fun setName(value: String) {
        if (value == "-") {
            useStdout = true
            usePersonalFormat = false
        } else {
            useStdout = false
            usePersonalFormat = true
            myPersonalFile = value
        }
    }

    /**
     * The tag is the initial part of the standard filename.  Setting this
     * implies that the date should be included in the filename.
     */
    fun setTag(value: String) {
        useStdout = false
        usePersonalFormat = false
        myTag = value
    }

    /**
     * Enables this LogDescriptor for use.  Most obvious effect is that
     * 'stream' is initialized.
     *
     * @throws Exception if a logfile could not be opened.  The contents of the
     * exception are irrelevant, as this method logs the problem.
     */
    @Throws(Exception::class)
    fun startUsing(previous: TraceLogDescriptor?) {
        if (useStdout) {
            stream = System.out
            myFile = null
            return
        }
        val nextFile: File?
        nextFile = if (previous != null) {
            previous.myNextFile
        } else {
            desiredLogFile()
        }
        if (nextFile!!.isDirectory) {
            throw IOException("opening directory as a logfile")
        }
        try {
            stream = PrintStream(FileOutputStream(nextFile), false, Charset.defaultCharset())
            myFile = nextFile
            myNextFile = null
        } catch (e: SecurityException) {
            throw e
        } catch (e: FileNotFoundException) {
            throw e
        }
    }

    /**
     * Cease using this LogDescriptor.  The most obvious effect is that
     * 'stream' is now null.  Behind the scenes, any open file is closed.
     * You can alternate stopUsing() and startUsing() an arbitrary number of
     * times.
     */
    fun stopUsing() {
        if (stream !== System.out) {
            stream!!.close()
        }
        stream = null
        myFile = null
    }
}
