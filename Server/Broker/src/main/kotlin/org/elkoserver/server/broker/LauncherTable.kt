package org.elkoserver.server.broker

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteralArray
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.objdb.ObjDb
import org.elkoserver.util.trace.slf4j.Gorgel
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
internal class LauncherTable @JsonMethod("ref", "launchers") constructor(private val myRef: String, launchers: Array<Launcher>) : Encodable {

    /**
     * Map from launcher names to launcher descriptors.
     */
    internal val myLaunchers = launchers.associateBy { it.componentName }

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
            JsonLiteralFactory.type("launchertable", control).apply {
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
            JsonLiteralArray().apply {
                for (launcher in myLaunchers.values) {
                    addElement(launcher)
                }
                finish()
            }

    /**
     * Save this launcher table to the repository if it has changed.
     *
     * @param objDb The object database to save into.
     */
    fun checkpoint(objDb: ObjDb?) {
        if (amDirty && objDb != null) {
            objDb.putObject(myRef, this, null, false, null)
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
     * @param componentName          Component name.
     * @param myLaunchScript        Launch script.
     * @param optInitial    Optional "initial" flag; defaults to false.
     * @param optRunSetting Optional "run setting" flag; defaults to true.
     */
    internal class Launcher @JsonMethod("name", "script", "initial", "on") constructor(
            internal val componentName: String,
            private val myLaunchScript: String, optInitial: OptBoolean, optRunSetting: OptBoolean) : Encodable {
        internal lateinit var gorgel: Gorgel

        /**
         * Flag that is true if this launcher should be executed when starting
         * in Initial mode.
         */
        var isInitialLauncher = optInitial.value(false)

        /**
         * Flag that is true if this launcher should be executed when starting
         * in Restart or Recover mode.
         */
        var isRunSettingOn = optRunSetting.value(true)

        /**
         * Encode this launcher.
         *
         * @param control Encode control determining what flavor of encoding
         * should be done.
         * @return a JSON literal representing this launcher.
         */
        override fun encode(control: EncodeControl) =
                JsonLiteralFactory.type("launcher", control).apply {
                    addParameter("name", componentName)
                    addParameter("script", myLaunchScript)
                    addParameter("on", isRunSettingOn)
                    if (isInitialLauncher) {
                        addParameter("initial", true)
                    }
                    finish()
                }

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
                    gorgel.i?.run { info("start process '$componentName'") }
                    isRunSettingOn = true
                    null
                } catch (e: IOException) {
                    gorgel.i?.run { info("process launch '$componentName' failed: $e") }
                    "fail $componentName $e"
                }
    }

    companion object {
        /* Possible startup modes.  Initial mode starts up a clean server from
       scratch based on configured initialization parameters.  Restart restarts
       a previously stopped server.  Recover starts up a previously crashed
       server. */
        const val START_INITIAL = 1
        const val START_RECOVER = 2
        const val START_RESTART = 3
    }
}
