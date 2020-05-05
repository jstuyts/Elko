package org.elkoserver.server.context

import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralArray

/**
 * Collection class to hold all the mods attached to a basic object.
 *
 * An object only acquires a ModSet if it actually has mods attached.
 */
class ModSet private constructor() {
    /** The mods themselves, indexed by class.  */
    private val myMods: MutableMap<Class<*>, Mod> = HashMap()

    /** Auxiliary mods table, to lookup mods by superclass.  */
    private var mySuperMods: MutableMap<Class<*>, Mod> = HashMap()

    /**
     * Constructor.
     *
     * @param mods  Array of mods to generate the mod set from.
     */
    constructor(mods: Array<Mod>?) : this() {
        if (mods != null) {
            for (mod in mods) {
                addMod(mod)
            }
        }
    }

    /**
     * Add a mod to the set.
     *
     * @param mod  The mod to add.
     */
    private fun addMod(mod: Mod) {
        var modClass: Class<*> = mod.javaClass
        myMods[modClass] = mod
        modClass = modClass.superclass
        while (modClass != Mod::class.java && Mod::class.java.isAssignableFrom(modClass)) {
            mySuperMods[modClass] = mod
            modClass = modClass.superclass
        }
    }

    fun removeMod(mod: Mod) {
        myMods.remove(mod.javaClass)
        redetermineSuperMods()
    }

    private fun redetermineSuperMods() {
        mySuperMods = HashMap()
        myMods.forEach { (modClass: Class<*>, mod: Mod) ->
            var modClassX = modClass
            while (modClassX != Mod::class.java && Mod::class.java.isAssignableFrom(modClassX)) {
                mySuperMods[modClassX] = mod
                modClassX = modClassX.superclass
            }
        }
    }

    /**
     * Make all these mods become mods of something.
     *
     * @param object  The object to which these mods are to be attached.
     */
    fun attachTo(`object`: BasicObject) {
        for (mod in myMods.values) {
            mod.attachTo(`object`)
        }
    }

    /**
     * Encode this mods list as a JSONLiteralArray object.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSONLiteralArray object representing this mods list.
     */
    fun encode(control: EncodeControl): JSONLiteralArray {
        val result = JSONLiteralArray(control)
        myMods.values
                .filter { control.toClient() || !it.isEphemeral }
                .forEach(result::addElement)
        result.finish()
        return result
    }

    /**
     * Get a mod from the set, if there is one.
     *
     * @param type  The class of mod sought.
     *
     * @return the mod of the given class, or null if there is no such mod.
     */
    fun <TMod> getMod(type: Class<TMod>): TMod? {
        @Suppress("UNCHECKED_CAST") var result = myMods[type] as TMod?
        if (result == null) {
            @Suppress("UNCHECKED_CAST")
            result = mySuperMods[type] as TMod?
        }
        return result
    }

    /**
     * Arrange to inform any mods that have expressed an interest that the
     * object they are mod of is now complete.  If the mod is a
     * ContextShutdownWatcher, automatically register interest in the shutdown
     * event with the context.
     */
    fun objectIsComplete() {
        for (mod in myMods.values) {
            if (mod is ObjectCompletionWatcher) {
                mod.`object`().contextor().addPendingObjectCompletionWatcher(
                        mod as ObjectCompletionWatcher)
            }
            if (mod is ContextShutdownWatcher) {
                mod.context().registerContextShutdownWatcher(
                        mod as ContextShutdownWatcher)
            }
        }
    }

    /**
     * Remove from the mods list any mods that are marked as being ephemeral.
     */
    fun purgeEphemeralMods() {
        myMods.values.removeIf(Mod::isEphemeral)
        redetermineSuperMods()
    }

    companion object {
        /**
         * Add a mod to a set, creating the set if necessary to do so.
         *
         * @param modSet  The set to which the mod is to be added, or null if no
         * set exists yet.
         * @param mod  The mod to add.
         *
         * @return the set (created if necessary), with 'mod' in it.
         */
        fun withMod(modSet: ModSet?, mod: Mod) =
                (modSet ?: ModSet()).apply {
                    addMod(mod)
                }
    }
}
