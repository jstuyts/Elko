package org.elkoserver.server.context.test

import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.RandomUsingObject
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonLiteralFactory
import org.elkoserver.server.context.ItemMod
import org.elkoserver.server.context.Mod
import org.elkoserver.server.context.User
import java.util.Random
import kotlin.math.abs

/**
 * Mod to enable an item to function as a die.
 */
class Die @JsonMethod("sides") constructor(private val mySides: Int) : Mod(), ItemMod, RandomUsingObject {

    private lateinit var myRandom: Random

    override fun setRandom(random: Random) {
        myRandom = random
    }

    /**
     * Encode this mod for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this mod.
     */
    override fun encode(control: EncodeControl): JsonLiteral =
            JsonLiteralFactory.type("die", control).apply {
                addParameter("sides", mySides)
                finish()
            }

    /**
     * Message handler for the 'roll' message.
     *
     * This message requests a die roll.
     *
     * <u>recv</u>: ` { to:*REF*, op:"roll" } `
     *
     * <u>send</u>: ` { to:*REF*, op:"roll",
     * from:*REF*, value:*int* } `
     *
     * @param from  The user requesting the census.
     *
     * @throws MessageHandlerException if 'from' is not in the same context as
     * this mod, or if this mod is attached to a user and 'from' is not that
     * user.
     */
    @JsonMethod
    fun roll(from: User) {
        ensureSameContext(from)
        val value = abs(myRandom.nextInt()) % mySides + 1
        val announce = JsonLiteralFactory.targetVerb(`object`(), "roll").apply {
            addParameter("value", value)
            addParameter("from", from.ref())
            finish()
        }
        context().send(announce)
    }
}
