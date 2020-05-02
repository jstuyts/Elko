package org.elkoserver.server.broker

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.objdb.ObjDB
import org.elkoserver.util.trace.Trace
import java.io.IOException
import java.util.LinkedList
import java.util.StringTokenizer

/**
 * Holder of knowledge as to how to start external processes, normally for the
 * purpose of launching servers of various kinds.
 *
 * @param myRef  Ref of this table.  Usually there is only one and it is
 *    called "launchtable".
 * @param launchers  Array of launcher configurations.
 */
internal class LauncherTable @JSONMethod("ref", "launchers") constructor(private val myRef: String, launchers: Array<Launcher>) : Encodable {
    /**
     * Map from launcher names to launcher descriptors.
     */
    private val myLaunchers: MutableMap<String, Launcher> = HashMap()

    /**
     * Dirty flag for checkpointing changes to launcher settings.
     */
    private var amDirty = true

    /**
     * Encode this table.
     *
     * @param control Encode control determining what flavor of encoding
     * should be done.
     * @return a JSON literal representing this table.
     */
    override fun encode(control: EncodeControl) =
            JSONLiteralFactory.type("launchertable", control).apply {
                addParameter("ref", myRef)
                addParameter("launchers", myLaunchers.values)
                finish()
            }

    /**
     * Encode an array of the launcher descriptions for benefit of the
     * admin console.
     *
     * @return a JSON literal representing the launchers.
     */
    fun encodeAsArray() =
            JSONLiteralArray().apply {
                for (launcher in myLaunchers.values) {
                    addElement(launcher)
                }
                finish()
            }

    /**
     * Save this launcher table to the repository if it has changed.
     *
     * @param odb The object database to save into.
     */
    fun checkpoint(odb: ObjDB?) {
        if (amDirty && odb != null) {
            odb.putObject(myRef, this, null, false, null)
            amDirty = false
        }
    }

    /**
     * Do whatever launches should be done as part of cluster startup.
     *
     * @param startMode What sort of startup this is.
     */
    fun doStartupLaunches(startMode: Int) {
        for (launcher in myLaunchers.values) {
            if (startMode == START_INITIAL && launcher.isInitialLauncher ||
                    launcher.isRunSettingOn) {
                launcher.launch()
                if (!launcher.isRunSettingOn) {
                    launcher.isRunSettingOn = true
                    amDirty = true
                }
            }
        }
    }

    /**
     * Execute one of the launchers.
     *
     * @param component Name of the component to be launched.
     * @return null on success, an error string on failure.
     */
    fun launch(component: String): String? {
        val launcher = myLaunchers[component]
        return if (launcher != null) {
            if (!launcher.isRunSettingOn) {
                launcher.isRunSettingOn = true
                amDirty = true
            }
            launcher.launch()
        } else {
            "fail $component not found"
        }
    }

    /**
     * Set the "initial launcher" flag for a component.  This flag controls
     * whether the component should be started when the startup mode is
     * Initial.
     *
     *
     * This method is has no effect if the named component doesn't exist.
     *
     * @param component The name of the component.
     * @param flag      The setting for the flag: true=>launch this component
     * when starting in Initial mode; false=> don't.
     */
    fun setInitialLauncher(component: String, flag: Boolean) {
        val launcher = myLaunchers[component]
        if (launcher != null) {
            launcher.isInitialLauncher = flag
            amDirty = true
        }
    }

    /**
     * Set the "run setting" flag for a component.  This flag controls
     * whether the component should be started when the startup mode is
     * Restart or Recover.
     *
     *
     * This method is has no effect if the named component doesn't exist.
     *
     * @param component The name of the component.
     */
    fun setRunSettingOn(component: String) {
        val launcher = myLaunchers[component]
        if (launcher != null) {
            launcher.isRunSettingOn = false
            amDirty = true
        }
    }

    /**
     * Launcher for a single cluster component.
     *
     * @param myComponentName          Component name.
     * @param myLaunchScript        Launch script.
     * @param optInitial    Optional "initial" flag; defaults to false.
     * @param optRunSetting Optional "run setting" flag; defaults to true.
     */
    internal class Launcher @JSONMethod("name", "script", "initial", "on") constructor(
            private val myComponentName: String,
            private val myLaunchScript: String, optInitial: OptBoolean, optRunSetting: OptBoolean) : Encodable {

        /**
         * Flag that is true if this launcher should be executed when starting
         * in Initial mode.
         */
        var isInitialLauncher: Boolean

        /**
         * Flag that is true if this launcher should be executed when starting
         * in Restart or Recover mode.
         */
        var isRunSettingOn: Boolean

        /**
         * Encode this launcher.
         *
         * @param control Encode control determining what flavor of encoding
         * should be done.
         * @return a JSON literal representing this launcher.
         */
        override fun encode(control: EncodeControl) =
                JSONLiteralFactory.type("launcher", control).apply {
                    addParameter("name", myComponentName)
                    addParameter("script", myLaunchScript)
                    addParameter("on", isRunSettingOn)
                    if (isInitialLauncher) {
                        addParameter("initial", true)
                    }
                    finish()
                }

        /**
         * Obtain the name of the component this launcher launches.
         *
         * @return the component name.
         */
        fun componentName() = myComponentName

        /**
         * Execute this launcher by starting a new process that begins by
         * executing the configured script.
         *
         * @return null on success, an error string on failure.
         */
        fun launch() =
                try {
                    val parser = StringTokenizer(myLaunchScript)
                    val exploded: MutableList<String> = LinkedList()
                    while (parser.hasMoreTokens()) {
                        exploded.add(parser.nextToken())
                    }
                    ProcessBuilder(exploded).start()
                    tr!!.eventm("start process '$myComponentName'")
                    isRunSettingOn = true
                    null
                } catch (e: IOException) {
                    tr!!.eventm("process launch '" + myComponentName + "' failed: " +
                            e)
                    "fail $myComponentName $e"
                }

        init {
            isInitialLauncher = optInitial.value(false)
            isRunSettingOn = optRunSetting.value(true)
        }
    }

    companion object {
        /**
         * Trace object for error messages and logging.
         */
        private var tr: Trace? = null

        /* Possible startup modes.  Initial mode starts up a clean server from
       scratch based on configured initialization parameters.  Restart restarts
       a previously stopped server.  Recover starts up a previously crashed
       server. */
        const val START_INITIAL = 1
        const val START_RECOVER = 2
        const val START_RESTART = 3

        /**
         * Assign the trace object.  Note that this is static.
         *
         * @param appTrace The trace object to use.
         */
        @JvmStatic
        fun setTrace(appTrace: Trace?) {
            tr = appTrace
        }
    }

    init {
        for (launcher in launchers) {
            myLaunchers[launcher.componentName()] = launcher
        }
    }
}
