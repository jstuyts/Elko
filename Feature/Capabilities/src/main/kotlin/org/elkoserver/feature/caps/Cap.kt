package org.elkoserver.feature.caps

import com.grack.nanojson.JsonObject
import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.Deliverer
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptInteger
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.getOptionalBoolean
import org.elkoserver.json.getOptionalLong
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.Item
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.ObjectCompletionWatcher
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.msgDelete
import java.time.Clock

/**
 * Base class for implementing capability mods that grant access to privileged
 * operations.
 */
abstract class Cap internal constructor(desc: JsonObject) : Mod(), ObjectCompletionWatcher, ClockUsingObject {
    /**
     * Scope flag: if true, holder can transfer the capability to another
     * holder.
     */
    private var amTransferrable = desc.getOptionalBoolean("transferrable", true)

    /**
     * Scope flag: if true, holder can delete this capability.
     */
    private var amDeletable = desc.getOptionalBoolean("deletable", true)

    /**
     * Expiration time.  After this time, this capability will no longer
     * function.  Value is system time in milliseconds.  A value of 0 indicates
     * that the capability never expires.  A value of -1 indicates that the
     * capability is ephemeral, i.e., that it expires when the holder exits.
     */
    private var myExpiration = desc.getOptionalLong("expiration", 0L)

    private lateinit var clock: Clock

    override fun setClock(clock: Clock) {
        this.clock = clock
    }

    /**
     * Mark the item as visible only to its holder.
     *
     * Application code should not call this method.
     */
    override fun objectIsComplete() {
        (`object`() as? Item)?.setVisibility(BasicObject.VIS_PERSONAL)
    }

    /**
     * Encode the basic capability parameters as part of encoding this
     * capability mod.  This method must be called by the encode() method of
     * each subclass.
     *
     * @param lit  The JSONLiteral into which this capability mod is being
     * encoded.
     */
    fun encodeDefaultParameters(lit: JsonLiteral) {
        if (!amDeletable) {
            lit.addParameter("deletable", amDeletable)
        }
        if (!amTransferrable) {
            lit.addParameter("transferrable", amTransferrable)
        }
        if (0 < myExpiration) {
            lit.addParameter("expiration", myExpiration)
        }
    }

    /**
     * Guard function to guarantee that an operation being attempted by a user
     * is being applied to a capability that is actually available to that
     * user.  To be available, the capability must be attached to an object
     * that is reachable by the user and it must not be expired.  If this
     * capability is not available, this method will throw a [MessageHandlerException].
     *
     * @throws MessageHandlerException if the test fails.
     */
    fun ensureValid(from: User) {
        ensureReachable(from)
        if (isExpired) {
            throw MessageHandlerException("capability expired")
        }
    }

    /**
     * Test if this capability has expired.
     *
     * @return true if this capability has expired, false if it is still OK to
     * use.
     */
    val isExpired: Boolean
        get() = 0 < myExpiration && myExpiration < clock.millis()

    /**
     * Handle a 'delete' message.  This is a request from a client to delete
     * the capability object.
     *
     * This request will be rejected if the capability is marked as
     * undeletable and is not expired.
     *
     * <u>recv</u>: ` { to:*REF*, op:"delete" } `<br></br>
     * <u>send</u>: ` { to:*REF*, op:"delete" } `
     *
     * @param from  The user who sent the message.
     */
    @JsonMethod
    fun delete(from: User) {
        ensureReachable(from)
        if (!amDeletable && !isExpired) {
            throw MessageHandlerException("attempt to delete non-deletable capability")
        }
        val cap = `object`() as Item
        (holder() as Deliverer).send(msgDelete(cap))
        cap.delete()
    }

    /**
     * Handle a 'setlabel' message.  This is a request from a client to change
     * the label of the capability object.
     *
     * <u>recv</u>: ` { to:*REF*, op:"setlabel",
     * label:*STR* } `<br></br>
     * <u>send</u>: no reply is sent.
     *
     * @param from  The user who sent the message.
     * @param label  The new label string.
     */
    @JsonMethod("label")
    fun setlabel(from: User, label: String) {
        ensureReachable(from)
        `object`().name = label
    }

    /**
     * Handle a 'transfer' message.  This is a request from a client to pass
     * possession of the capability object to somebody else.
     *
     * This request will be rejected if the capability is marked as
     * untransferrable.  Yes, this is unenforceable due to the possibility of
     * proxying, but marketing solipsism must be served.
     *
     * <u>recv</u>: ` { to:*REF*, op:"transfer", dest:*DESTREF* } `<br></br>
     * <u>send</u>: ` { to:*REF*, op:"delete" } `<br></br>
     * <u>send</u>: ` { to:*DESTREF*, op:"make", ... } `
     *
     * @param from  The user who sent the message.
     * @param destRef  Reference to user or context that is to receive the
     * capability.
     */
    @JsonMethod("dest")
    fun transfer(from: User, destRef: String) {
        ensureReachable(from)
        if (!amTransferrable) {
            throw MessageHandlerException("attempt to transfer non-transferrable capability")
        }
        val newHolder = context()[destRef]
        if (newHolder == null || newHolder is Item) {
            // XXX TODO IMPORTANT: doesn't work if dest is offline
            throw MessageHandlerException("invalid transfer destination $destRef")
        }
        val cap = `object`() as Item
        (holder() as Deliverer).send(msgDelete(cap))
        cap.setContainer(newHolder)
        cap.sendObjectDescription(newHolder as Deliverer, newHolder)
    }

    /**
     * Handle a 'spawn' message.  This is a request from a client to create a
     * copy of the capability object, with possibly reduced scope of powers.
     *
     * This method will reject, as an illegal rights amplification, any
     * attempt to spawn a capability that is undeletable when the base
     * capability is deletable, transferrable when the base capability is
     * untransferrable, or which has a later expiration time than the base
     * capability.
     *
     * If 'dest' designates a different holder, the operation will also be
     * regarded as a transfer and subjected to all the same checks as a call to
     * [transfer].
     *
     * <u>recv</u>: ` { to:*REF*, op:"spawn", dest:*optDESTREF*,
     * transferrable:*optBOOL*,
     * duration:*optLONG*,
     * expiration:*optLONG* } `<br></br>
     * <u>send</u>: ` { to:*DESTREF*, op:"make", ... } `
     *
     * @param from  The user who sent the message.
     * @param dest  Reference to container into which the new capability is to
     * be placed.  If omitted, defaults to the sending user.  If this
     * capability is non-transferrable, must refer to the sending user or a
     * container contained by the sending user.
     * @param transferrable  Flag indicating whether the new capability is to
     * be transferrable.  If omitted, defaults to the same value as this
     * capability.  It is not permitted to set this parameter to
     * true if this capability is not itself transferrable.
     * @param deleteable  Flag indicating whether the new capability is to be
     * deleteable.  If omitted, defaults to true.  It is not permitted to
     * set this parameter to false if this capability is itself deletable.
     * @param duration  Expiration time of the new capability, expressed as an
     * offset (in milliseconds) from the present.  A value of 0 (the
     * default) indicates that the capability is permanent.
     * @param expiration  Expiration time of the new capability, expressed as
     * an absolute system time (in milliseconds).
     */
    @JsonMethod("dest", "transferrable", "deletable", "duration", "expiration")
    fun spawn(from: User, dest: OptString, transferrable: OptBoolean,
              deleteable: OptBoolean, duration: OptInteger,
              expiration: OptInteger) {
        ensureReachable(from)
        val destRef = dest.valueOrNull()
        val container = if (destRef == null) {
            from
        } else {
            context()[destRef]
        }
        if (container == null) {
            throw MessageHandlerException("can't find spawn destination $destRef")
        }
        val newTransferrable = transferrable.value(amTransferrable)
        val newDeletable = transferrable.value(true)
        if (newTransferrable && !amTransferrable ||
                !newDeletable && amDeletable) {
            throw MessageHandlerException("illegal rights amplification")
        }
        if (!amTransferrable && container.holder() !== from) {
            throw MessageHandlerException("capability is not transferrable")
        }
        val newDuration = duration.value(0).toLong()
        var newExpiration = expiration.value(0).toLong()
        if (newExpiration == 0L && newDuration == 0L) {
            newExpiration = myExpiration
        } else if (newExpiration == 0L) { /* newDuration != 0 */
            newExpiration = newDuration + clock.millis()
        } else if (newDuration != 0L) { /* && newExpiration != 0 */
            throw MessageHandlerException("can't specify both duration and expiration")
        }
        val expireOK = when (myExpiration) {
            0L -> true
            -1L -> newExpiration == -1L
            else -> newExpiration <= myExpiration
        }
        if (!expireOK) {
            throw MessageHandlerException("illegal rights amplification")
        }
        val capItem = container.createItem(`object`().name!!, false, true)
        try {
            clone() as Cap
        } catch (e: CloneNotSupportedException) {
            throw MessageHandlerException("this can't happen")
        }.apply {
            amTransferrable = newTransferrable
            amDeletable = newDeletable
            myExpiration = newExpiration
            attachTo(capItem)
        }
        capItem.objectIsComplete()
        capItem.sendObjectDescription(capItem.holder() as Deliverer, container)
    }
}
