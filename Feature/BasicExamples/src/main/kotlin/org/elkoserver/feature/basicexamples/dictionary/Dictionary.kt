package org.elkoserver.feature.basicexamples.dictionary

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.GeneralMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User

/**
 * Mod to associate a server-moderated hashtable with its object.  This mod
 * may be attached to a context, user or item.
 *
 * @param names  Array of variable names.
 * @param values  Parallel array of values for those variables.
 * @param persist  If true, make sure any changes get saved to disk; if
 *    false (the default), changes are ephemeral.
 */
class Dictionary @JsonMethod("names", "values", "persist") constructor(names: Array<String>, values: Array<String>, persist: OptBoolean) : Mod(), GeneralMod {
    private val myVars: MutableMap<String, String> = HashMap<String, String>().apply {
        names.forEachIndexed { index, name ->
            this[name] = values[index]
        }
    }
    private val amPersistent = persist.value(false)
    private val myOriginalVars: MutableMap<String, String>?

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral {
        val vars =
                if (control.toClient() || amPersistent) {
                    myVars
                } else {
                    myOriginalVars
                }
        return JsonLiteralFactory.type("dictionary", control).apply {
            vars?.let {
                addParameter("names", it.keys.toTypedArray() as Array<out String?>)
                addParameter("values", it.values.toTypedArray() as Array<out String?>)
            }
            if (control.toRepository() && amPersistent) {
                addParameter("persist", amPersistent)
            }
            finish()
        }
    }

    /**
     * Message handler for the 'delvar' message.
     *
     * This message is a request to delete of one or more of the variables
     * from the set.  If the operation is successful, a corresponding 'delvar'
     * message is broadcast to the context.
     *
     * Warning: This message is not secure.  As implemented today, anyone
     * can delete variables.
     *
     * <u>recv</u>: ` { to:*REF*, op:"delvar",
     * names:*STR[]* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"delvar",
     * names:*STR[[* } `
     *
     * @param from  The user who sent the message.
     * @param names  Names of the variables to remove.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod.
     */
    @JsonMethod("names")
    fun delvar(from: User, names: Array<String>) {
        ensureSameContext(from)
        for (name in names) {
            myVars.remove(name)
        }
        if (amPersistent) {
            markAsChanged()
        }
        context().send(msgDelvar(`object`(), from, names))
    }

    /**
     * Message handle for the 'setvar' message.
     *
     * This message is a request to change the value of one or more of the
     * variables (or to assign a new variable).  If the operation is
     * successfull, a corresponding 'setvar' message is broadcast to the
     * context.
     *
     * Warning: This message is not secure.  As implemented today, anyone
     * can modify variables.
     *
     * <u>recv</u>: ` { to:*REF*, op:"setvar", names:*STR[]*,
     * values:*STR[]* } `<br></br>
     *
     * <u>send</u>: ` { to:*REF*, op:"setvar", names:*STR[]*,
     * values:*STR[]* } `
     *
     * @param from  The user who sent the message.
     * @param names  Names of the variables to assign.
     * @param values  The values to set them to.  Each element of the array is
     * the value for the corresponding element of 'names.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod.
     */
    @JsonMethod("names", "values")
    fun setvar(from: User, names: Array<String>, values: Array<String>) {
        ensureSameContext(from)
        if (names.size != values.size) {
            throw MessageHandlerException(
                    "parameter array lengths unequal")
        }
        for (i in names.indices) {
            myVars[names[i]] = values[i]
        }
        if (amPersistent) {
            markAsChanged()
        }
        context().send(msgSetvar(`object`(), from, names, values))
    }

    init {
        myOriginalVars =
                if (amPersistent) {
                    null
                } else {
                    val nameCount = names.size
                    HashMap<String, String>(nameCount, 1.0f).apply {
                        names.forEachIndexed { index, name ->
                            this[name] = values[index]
                        }
                    }
                }
    }
}
