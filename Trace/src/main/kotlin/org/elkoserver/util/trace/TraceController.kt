package org.elkoserver.util.trace

import org.elkoserver.foundation.properties.ElkoProperties
import java.time.Clock
import java.util.*

/**
 * The single trace controller which manages the operation of the tracing
 * system.
 */
class TraceController(private val theAcceptor: TraceMessageAcceptor, clock: Clock) {
    /**
     * Trace threshold that applies to subsystems that haven't been given
     * specific values.
     */
    var theDefaultThreshold = Level.WARNING
        private set

    /**
     * Change the default tracing threshold.
     */
    private fun changeDefaultThreshold(newThreshold: Level) {
        theDefaultThreshold = newThreshold
    }

    internal val acceptor = theAcceptor

    val factory = TraceFactory(this, clock)

    /**
     * Change a specific Trace to have its own trace priority threshold OR
     * change it to resume tracking the default.
     */
    private fun changeSubsystemThreshold(subsystem: String,
                                         value: String) {
        val tr = factory.trace(subsystem)
        if (value.equals(DEFAULT_NAME, ignoreCase = true)) {
            tr.setThreshold(theDefaultThreshold)
        } else {
            tr.setThreshold(stringToTraceLevel(value))
        }
    }

    /**
     * Set the trace control properties from a given set of properties.
     *
     * IMPORTANT:  The properties are processed in an unpredictable order.
     */
    private fun setProperties(props: ElkoProperties, additionalProperties: Map<String, String>) {
        for (property in props.stringPropertyNames()) {
            setProperty(property, props.getProperty(property))
        }
        for ((key, value) in additionalProperties) {
            setProperty(key, value)
        }
    }

    /**
     * Set one of the trace control properties.
     *
     * If the given key names a tracing property, process its value.  Note that
     * it is not an error for the key to have nothing to do with tracing; in
     * that case, it's ignored.  It *is* an error for the value to be
     * <tt>null</tt>.
     *
     * @param key  The name of the property to set.
     * @param value  The value to set it to, if it is a trace control property.
     */
    private fun setProperty(key: String, value: String?) {
        /* Note: synchronization is the responsibility of the objects whose
           properties are being changed. */
        val trimmedKey = key.trim { it <= ' ' }
        val nonNullValue = value?.trim { it <= ' ' } ?: error("Trace property value cannot be null.")
        try {
            val lowerKey = trimmedKey.toLowerCase(Locale.ENGLISH)
            if (lowerKey.startsWith("trace_")) {
                val afterUnderscore = trimmedKey.substring(6)
                if (afterUnderscore.equals(DEFAULT_NAME, ignoreCase = true)) {
                    changeDefaultThreshold(stringToTraceLevel(nonNullValue))
                } else {
                    changeSubsystemThreshold(afterUnderscore, nonNullValue)
                }
            } else if (lowerKey.startsWith("tracelog_")) {
                theAcceptor.setConfiguration(trimmedKey.substring(9), nonNullValue)
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

        /* Other properties are ignored, because this method may be called from
           setProperties, which is given a whole mess of properties, some
           irrelevant to Trace. */
    }

    /**
     * Start the operation of the trace system.  Prior to this call, [ ] objects may be obtained and messages may be sent to them.
     * However, the messages will be queued up until this routine is called.
     * (Note that the messages will be governed by the default thresholds.)
     *
     * Applications should not need to call this method, since normally it is
     * called automatically by the server boot class (e.g., `Boot`).
     *
     * @param props  The initial set of properties provided by the user.  They
     * override the defaults.  They may be changed later.
     */
    fun start(props: ElkoProperties) {
        val additionalProperties: MutableMap<String, String> = HashMap()
        if (!props.containsProperty("tracelog_write")) {
            additionalProperties["tracelog_write"] = "true"
        }
        if (!props.containsProperty("tracelog_name") &&
                !props.containsProperty("tracelog_dir") &&
                !props.containsProperty("tracelog_tag")) {
            additionalProperties["tracelog_name"] = "-"
        }
        setProperties(props, additionalProperties)
        theAcceptor.setupIsComplete()
    }

    companion object {
        private const val DEFAULT_NAME = "default"
    }
}
