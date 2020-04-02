package org.elkoserver.util.trace.acceptor.file

import org.elkoserver.util.trace.TraceMessage
import org.elkoserver.util.trace.TraceMessageAcceptor
import java.time.Clock
import java.util.*

/**
 * This class manages dumping of messages to the semi-permanent on-disk log.
 * Will queue messages until it's pointed at a log file or stdout.  Messages
 * will be redirected to stdout if a given logfile can't be opened.
 */
class TraceLog(clock: Clock) : TraceMessageAcceptor {
    private val stdout = TraceLogDescriptor(clock)

    init {
        stdout.usePersonalFormat = false
        stdout.useStdout = true
    }

    /** What to do with full or existing log files: rollover or empty.  */
    private var myVersionAction: ClashAction = STARTING_LOG_VERSION_ACTION

    /** Flag controlling whether a log file should be written at all.  */
    private var amWriteEnabled = false

    /** Log file size above which the file will be rolled over, in chars.  */
    private var myMaxSize = STARTING_LOG_SIZE_THRESHOLD

    /** Flag that max size was explicitly set rather than defaulted.  */
    private var amMaxSizeSet = false

    /** Number of characters in the current log file.  */
    private var myCurrentSize: Long = 0

    /** Frequency with which log files are rolled over, in milliseconds.  */
    private var myRolloverFrequency: Long = 0

    /** Time of next scheduled log file rollover, or 0 if rollover is off  */
    private var myNextRolloverTime: Long = 0

    /** Log to which messages are currently flowing, or null if none yet.  */
    private var myCurrent: TraceLogDescriptor? = null

    /**
     * The user can change the characteristics of this log descriptor, then
     * redirect the log to it.  Characteristics are changed via properties like
     * "tracelog_tag".  Redirection is done via "tracelog_reopen".
     */
    private var myPending = TraceLogDescriptor(clock)

    /** True if all the initialization properties have been processed.  */
    private var mySetupComplete = false

    /** Buffer for building log message strings in.  */
    private val myStringBuilder = StringBuilder(200)

    /** Queue for messages prior to log init and while switching log files.  */
    private var myQueuedMessages: MutableList<TraceMessage>? = null

    /**
     * Accept a message for the log.  It will be discarded if both writing
     * and the queue are turned off.
     */
    @Synchronized
    override fun accept(message: TraceMessage) {
        if (isAcceptingMessages) {
            if (isQueuing) {
                myQueuedMessages!!.add(message)
            } else {
                outputMessage(message)
            }
        }
    }

    /**
     * Take a message and actually output it to the log.  In particular, the
     * queue of pending messages is bypassed, because this method is used in
     * the process of draining that queue.
     */
    private fun outputMessage(message: TraceMessage) {
        message.stringify(myStringBuilder)
        val output = myStringBuilder.toString()
        myCurrent!!.stream!!.println(output)
        /* Note: there's little point in checking for an output error.  We
           can't put the trace in the log, and there's little chance the user
           would see it in the trace buffer.  So we ignore it, with regret. */
        myCurrentSize += output.length + LINE_SEPARATOR_LENGTH.toLong()
        if (myCurrentSize > myMaxSize) {
            rolloverLogFile()
        } else if (myNextRolloverTime != 0L &&
                myNextRolloverTime < message.timestamp) {
            do {
                myNextRolloverTime += myRolloverFrequency
            } while (myNextRolloverTime < message.timestamp)
            rolloverLogFile()
        }
    }

    /**
     * Call to initialize a log when logging is just beginning (or resuming
     * after having been turned off).  There is no current log, so nothing is
     * written to it.  If the pending log cannot be opened, standard output is
     * used as the log.  In any case, the queue is drained just before the
     * method returns.
     */
    private fun beginLogging() {
        try {
            /* Rename any existing file */
            myPending.startUsing(null)
        } catch (e: Exception) {
            /* Couldn't open the log file.  Bail to stdout. */
            myCurrent = stdout
            try {
                myCurrent!!.startUsing(null)
            } catch (ignore: Exception) {
                assert(false) { "Exceptions shouldn't happen opening stdout." }
            }
            drainQueue()
            return
        }
        myCurrent = myPending
        myPending = myCurrent!!.clone() as TraceLogDescriptor
        myCurrentSize = 0
        drainQueue()
    }

    /**
     * Change how a full logfile handles its version files.  "one" or "1" means
     * that there will be at most one file, which will be overwritten if
     * needed.  "many" means a new versioned file with a new name should be
     * created each time the base file fills up.  Has effect when the next log
     * file fills up.
     */
    private fun changeVersionFileHandling(newBehavior: String) {
        val lowerCaseNewBehavior = newBehavior.toLowerCase(Locale.ENGLISH)
        if (lowerCaseNewBehavior == "one" || newBehavior == "1") {
            myVersionAction = ClashAction.Overwrite
        } else if (lowerCaseNewBehavior == "many") {
            myVersionAction = ClashAction.Add
        }
    }

    /**
     * Change the default directory in which log files live.  Has effect only
     * when a new logfile is opened.
     */
    private fun changeDir(value: String) {
        myPending.setDir(value)
    }

    /**
     * Explicitly set the name of the next logfile to open.  Overrides the
     * effect of "tracelog_dir" only if the given name is absolute.  Has effect
     * only when a new logfile is opened.
     */
    private fun changeName(value: String) {
        myPending.setName(value)
    }

    /**
     * Change the time-based log file rollover policy.  By default, log files
     * are only rolled over when they reach some size threshold, but by setting
     * this you can also make them rollover based on the clock.
     *
     * @param value  Rollover policy.  Valid values are "weekly", "daily",
     * "hourly", "none", or an integer number that expresses the rollover
     * frequency in minutes.
     */
    private fun changeRollover(value: String) {
        var freq = 0
        val startCal = Calendar.getInstance()
        when (val lowerCaseValue = value.toLowerCase(Locale.ENGLISH)) {
            "weekly" -> {
                freq = 7 * 24 * 60
                startCal[Calendar.DAY_OF_WEEK] = Calendar.SUNDAY
                startCal[Calendar.HOUR_OF_DAY] = 0
                startCal[Calendar.MINUTE] = 0
                startCal[Calendar.SECOND] = 0
                startCal[Calendar.MILLISECOND] = 0
            }
            "daily" -> {
                freq = 24 * 60
                startCal[Calendar.HOUR_OF_DAY] = 0
                startCal[Calendar.MINUTE] = 0
                startCal[Calendar.SECOND] = 0
                startCal[Calendar.MILLISECOND] = 0
            }
            "hourly" -> {
                freq = 60
                startCal[Calendar.MINUTE] = 0
                startCal[Calendar.SECOND] = 0
                startCal[Calendar.MILLISECOND] = 0
            }
            else -> if (lowerCaseValue != "none") {
                try {
                    freq = value.toInt()
                    if (freq < 1) {
                        freq = 0
                    } else {
                        val minute = startCal[Calendar.MINUTE]
                        startCal[Calendar.MINUTE] = minute / freq * freq
                        startCal[Calendar.SECOND] = 0
                        startCal[Calendar.MILLISECOND] = 0
                    }
                } catch (e: NumberFormatException) {
                }
            }
        }
        if (freq != 0) {
            myRolloverFrequency = freq * 60 * 1000.toLong()
            if (!amMaxSizeSet) {
                myMaxSize = Long.MAX_VALUE
            }
            val startTime = startCal.timeInMillis
            myNextRolloverTime = startTime + myRolloverFrequency
        } else {
            myNextRolloverTime = 0
        }
    }

    /**
     * Change the new maximum allowable size for a logfile.  Has effect on the
     * current logfile.  Note that the trace system does not prevent the log
     * from exceeding this size; it only opens a new log file as soon as it
     * does.
     */
    private fun changeSize(value: String) {
        var newSize: Long
        val lowerCaseValue = value.toLowerCase(Locale.ENGLISH)
        newSize = when (lowerCaseValue) {
            DEFAULT_NAME -> STARTING_LOG_SIZE_THRESHOLD
            "unlimited" -> Long.MAX_VALUE
            else -> try {
                value.toLong()
            } catch (e: NumberFormatException) {
                myMaxSize /* leave unchanged. */
            }
        }
        if (newSize < SMALLEST_LOG_SIZE_THRESHOLD) {
            newSize = myMaxSize
        }
        amMaxSizeSet = true
        myMaxSize = newSize
        if (myCurrentSize > myMaxSize) {
            rolloverLogFile()
        }
    }

    /**
     * Change the 'tag' (base of filename) that log files have.  Has effect only
     * when a new logfile is opened.
     */
    private fun changeTag(value: String) {
        myPending.setTag(value)
    }

    /**
     * The meaning of changeWrite is complicated.  Here are the cases when it's
     * used to turn writing ON.
     *
     * If setup is still in progress, the state variable 'amWriteEnabled' is
     * used to note that logging should begin when setupIsComplete() is called.
     *
     * If setup is complete, logging should begin immediately.  If logging has
     * already begun, this is a no-op.
     *
     * Here are the cases for turning writing OFF.
     *
     * If setup is not complete, the state variable 'amWriteEnabled' informs
     * setupIsComplete() that logging should not begin.
     *
     * If setup is complete, logging is stopped.  However, if it was already
     * stopped, the call is a no-op.
     *
     * There would be some merit in having a state machine implement all this.
     */
    private fun changeWrite(value: String) {
        val lowerCaseValue = value.toLowerCase(Locale.ENGLISH)
        if (lowerCaseValue == "true") {
            if (!amWriteEnabled) {
                amWriteEnabled = true
                startQueuing() /* it's ok if the queue already started. */
                if (mySetupComplete) {
                    beginLogging()
                }
            }
        } else if (lowerCaseValue == "false") {
            if (amWriteEnabled) {
                drainQueue() /* either write messages or discard them */
                amWriteEnabled = false
                if (mySetupComplete) {
                    myCurrent!!.stopUsing()
                    myCurrent = null
                } else {
                    assert(myCurrent == null)
                }
            }
        }
    }

    /**
     * Deal with messages accumulated in the queue.  If the log is turned on
     * (amWriteEnabled is true), they are written.  Otherwise, they are
     * discarded.  It is safe to call this routine without knowing whether
     * queuing is in progress.
     */
    private fun drainQueue() {
        if (amWriteEnabled && isQueuing) {
            val queueToDrain: List<TraceMessage>? = myQueuedMessages
            myQueuedMessages = null
            for (message in queueToDrain!!) {
                outputMessage(message)
            }
        }
        myQueuedMessages = null
    }

    /**
     * Call when the logfile fills up or reaches rollover time.  Reopens the
     * same log file.
     *
     * Standard output can never fill up, so this routine is a no-op when the
     * current size of text sent to standard out exceeds the maximum, except
     * that the current size is reset to zero.
     */
    private fun rolloverLogFile() {
        /* Preemptively set the log size back to zero.  This allows log
           messages about the fullness of the log to be placed into the log,
           without getting into an infinite recursion.
        */
        myCurrentSize = 0
        if (myCurrent!!.stream !== System.out) {
            shutdownAndSwap()
        }
    }

    /**
     * Call to switch to a log when another - with a different name - is
     * currently being used.  If the pending log cannot be opened, the current
     * log continues to be used.
     *
     * Before the old log is closed, a WORLD message is logged, directing the
     * reader to the new log.  Trace messages may be queued while the swap is
     * happening, but the queue is drained before the method returns.
     *
     * This routine is never called when the logfile fills - it's only used
     * when explicitly reopening a log file.  (tracelog_reopen=&lt;anything&gt;).
     */
    private fun hotSwap() {
        /* Finish the old log with a pointer to the new. */
        startQueuing() /* further messages should go to the new log. */
        try {
            /* rename an existing file, since it is not an earlier version of
               the new name we're using. */
            myPending.startUsing(null)
        } catch (e: Exception) {
            /* continue using current. */drainQueue()
            return
        }
        /* Stash old log name to print in new log. */
        myCurrent!!.stopUsing()
        myCurrent = myPending
        myCurrentSize = 0
        myPending = myCurrent!!.clone() as TraceLogDescriptor
        drainQueue()
    }

    /**
     * Test if this log is accepting messages.
     *
     * The log accepts messages if the "tracelog_write" property was set.
     * Before setup is completed, it also accepts and queues up messages.  When
     * setup is complete, it either posts or discards those queued messages,
     * depending on what the user wants.
     *
     * Queuing also happens transitorily while logs are being switched.
     *
     * @return true if this log is accepting messages, false if it is
     * discarding them.
     */
    private val isAcceptingMessages: Boolean
        get() = amWriteEnabled || isQueuing

    /**
     * Test if this log is queueing messages.
     *
     * The log queues messages during its startup time and while switching
     * between open log files.
     *
     * @return true if this log is currently queueing messages.
     */
    private val isQueuing: Boolean
        get() = myQueuedMessages != null

    /**
     * The gist of this routine is that it shuts down the current log and
     * reopens a new one (possibly with the same name, possibly with a
     * different name).  There are some special cases, because this routine
     * could be called before setup is complete (though using tracelog_reopen
     * in the initial Properties is deprecated).
     *
     * It's called before setup is complete and writing is not enabled.  The
     * behavior is the same as tracelog_write [the preferred interface].
     *
     * It's called before setup is complete and writing is enabled.  The effect
     * is that of calling tracelog_write twice (a warning).
     *
     * It's called after setup is complete and writing is not enabled.  The
     * behavior is the same as calling tracelog_write [again, the preferred
     * interface, because you're not "reopening" anything].
     *
     * It's called after setup is complete and writing is enabled.  This is the
     * way it's supposed to be used.  The current log is closed and the pending
     * log is opened.
     */
    private fun reopen() {
        if (!amWriteEnabled || !mySetupComplete) {
            changeWrite("true")
        } else if (myPending == myCurrent) {
            shutdownAndSwap()
        } else {
            hotSwap()
        }
    }

    /**
     * Modify the acceptor configuration based on a property setting.  Property
     * names here are not true property names but property names with the
     * "trace_" or "tracelog_" prefix stripped off.
     *
     * @param name  Property name
     * @param value   Property value
     */
    @Synchronized
    override fun setConfiguration(name: String, value: String) {
        when (name.toLowerCase(Locale.ENGLISH)) {
            "write" -> changeWrite(value)
            "dir" -> changeDir(value)
            "tag" -> changeTag(value)
            "name" -> changeName(value)
            "size" -> changeSize(value)
            "rollover" -> changeRollover(value)
            "versions" -> changeVersionFileHandling(value)
            "reopen" -> reopen()
        }
    }

    /**
     * Call this only after all properties have been processed.  It begins
     * logging, but only if tracelog_write or tracelog_reopen have been used,
     * or if the default behavior is to write.
     */
    @Synchronized
    override fun setupIsComplete() {
        if (amWriteEnabled) {
            beginLogging()
        }
        drainQueue()
        mySetupComplete = true
    }

    /**
     * Call to initialize a log when the same file is already open.  If the
     * pending log cannot be opened, standard output is used.
     *
     * Before the old log is closed, a WORLD message is logged, directing the
     * reader to the new log.  Trace messages may be queued while the swap is
     * happening, but the queue is drained before the method returns.
     *
     * This routine can be called to version a full logfile, or to explicitly
     * reopen the same logfile (via tracelog_reopen=&lt;anything&gt;).
     */
    private fun shutdownAndSwap() {
        /* In the old log, say what will happen.  Can't log it while it's
           happening, because that all goes to the new log.
        */
        myCurrent!!.prepareToRollover(myVersionAction)

        /* Stash old log name.  This is used if reopening fails and further
           logging is blurted to stdout.
        */
        myCurrent!!.stopUsing()
        startQueuing() /* further messages should go to the new log. */
        try {
            myPending.startUsing(myCurrent)
        } catch (e: Exception) {
            myCurrent = stdout
            myCurrentSize = 0
            try {
                myCurrent!!.startUsing(null)
            } catch (ignore: Exception) {
                assert(false) { "No exceptions when opening stdout." }
            }
            drainQueue()
            return
        }
        myCurrent = myPending
        myCurrentSize = 0
        myPending = myCurrent!!.clone() as TraceLogDescriptor
        drainQueue()
    }

    /**
     * Redirect trace messages to a queue.  Used while switching to a new log
     * file, or before setup is complete.
     *
     * It is harmless to call this routine twice.
     */
    private fun startQueuing() {
        /* NOTE: trace messages must not be generated by this routine, because
           it's called from the constructor.
        */
        if (!isQueuing) {
            myQueuedMessages = LinkedList()
        }
    }

    companion object {
        /* Trace log file defaults */
        private const val STARTING_LOG_SIZE_THRESHOLD: Long = 500000
        private const val SMALLEST_LOG_SIZE_THRESHOLD: Long = 1000
        private val STARTING_LOG_VERSION_ACTION = ClashAction.Add
        const val DEFAULT_NAME = "default"
        private val LINE_SEPARATOR_LENGTH = System.getProperty("line.separator").length
    }

    /**
     * Constructor.  Queue messages until setup is complete.
     */
    init {
        /*
          DANGER:  This constructor must be called as part of static
          initialization of TraceController.  Until that initialization is
          done, Trace should not be loaded.  Therefore, nothing in this
          constructor should directly or indirectly use a tracing function.
        */
        startQueuing()
    }
}
