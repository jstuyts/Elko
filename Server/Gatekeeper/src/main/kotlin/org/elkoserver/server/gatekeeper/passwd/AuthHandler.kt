package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.actor.BasicProtocolActor
import org.elkoserver.foundation.actor.BasicProtocolHandler
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.server.gatekeeper.Gatekeeper
import org.elkoserver.util.trace.slf4j.Gorgel
import java.security.MessageDigest
import java.util.Random
import java.util.function.Consumer

/**
 * Singleton 'auth' message handler object for the password authorizer.
 *
 * @param myAuthorizer  The password authorizer being administered.
 * @param myGatekeeper  The gatekeeper this handler is working for.
 */
internal class AuthHandler(private val myAuthorizer: PasswdAuthorizer, private val myGatekeeper: Gatekeeper, commGorgel: Gorgel, private val actorRandom: Random, private val actorMessageDigest: MessageDigest) : BasicProtocolHandler(commGorgel) {

    /**
     * Get this object's reference string.  This singleton object is always
     * known as 'auth'.
     *
     * @return a string referencing this object.
     */
    override fun ref() = "auth"

    /**
     * Handle the 'createactor' verb.
     *
     * Request that an actor entry be created.
     *
     * @param from  The administrator asking for the new entry.
     * @param optID  The ID of the actor.
     * @param optIID  The internal ID of the actor.
     * @param optName  The name of the actor.
     * @param optPassword  Password for the actor.
     * @param optCanSetPass  Flag that actor can set their own password.
     */
    @JsonMethod("id", "iid", "name", "password", "cansetpass")
    fun createactor(from: BasicProtocolActor, optID: OptString,
                    optIID: OptString, optName: OptString, optPassword: OptString,
                    optCanSetPass: OptBoolean) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        var id = optID.valueOrNull()
        var generated = false
        if (id == null) {
            id = myAuthorizer.generateID()
            generated = true
        }
        myAuthorizer.getActor(id,
                CreateActorRunnable(from, id, generated,
                        optIID.valueOrNull(),
                        optName.valueOrNull(),
                        optPassword.valueOrNull(),
                        optCanSetPass.value(true)))
    }

    private inner class CreateActorRunnable(private val myFrom: BasicProtocolActor, private var myID: String,
                                            private val amGenerated: Boolean, private val myInternalID: String?, private val myName: String?,
                                            private val myPassword: String?, private val myCanSetPass: Boolean) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                if (amGenerated) {
                    myID = myAuthorizer.generateID()
                    myAuthorizer.getActor(myID, this)
                } else {
                    myFrom.send(msgCreateActor(this@AuthHandler, myID,
                            "actor ID not available"))
                }
            } else {
                val actor = ActorDesc(myID, myInternalID, myName, myPassword, myCanSetPass, actorRandom, actorMessageDigest)
                myAuthorizer.addActor(actor)
                myFrom.send(msgCreateActor(this@AuthHandler, myID, null))
            }
        }
    }

    /**
     * Handle the 'createplace' verb.
     *
     * Request that a place name entry be created.
     *
     * @param from  The administrator asking for the new entry.
     * @param name  The name of the new place.
     * @param context  The context that it maps to.
     */
    @JsonMethod("name", "context")
    fun createplace(from: BasicProtocolActor, name: String, context: String) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.addPlace(name, context)
    }

    /**
     * Handle the 'deleteactor' verb.
     *
     * Request that an actor entry be removed.
     *
     * @param from  The administrator asking for the deletion.
     * @param id  The ID of the actor to be deleted.
     */
    @JsonMethod("id")
    fun deleteactor(from: BasicProtocolActor, id: String) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.removeActor(id, { obj: Any? ->
            from.send(msgDeleteActor(this@AuthHandler, id, obj as String?))
        })
    }

    /**
     * Handle the 'deleteplace' verb.
     *
     * Request that a place name entry be removed.
     *
     * @param from  The administrator asking for the deletion.
     * @param name  The name of the entry to be deleted.
     */
    @JsonMethod("name")
    fun deleteplace(from: BasicProtocolActor, name: String) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.removePlace(name)
    }

    /**
     * Handle the 'lookupactor' verb.
     *
     * Request that an actor entry be retrieved.
     *
     * @param from  The administrator asking for the lookup.
     * @param id  The ID of the actor to be looked up.
     */
    @JsonMethod("id")
    fun lookupactor(from: BasicProtocolActor, id: String) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getActor(id, { obj: Any? ->
            if (obj == null) {
                from.send(msgLookupActor(this@AuthHandler, id, null, null, "no such actor"))
            } else {
                val actor = obj as ActorDesc
                from.send(msgLookupActor(this@AuthHandler, id, actor.internalID(), actor.name, null))
            }
        })
    }

    /**
     * Handle the 'lookupplace' verb.
     *
     * Request that a place name entry be retrieved.
     *
     * @param from  The administrator asking for the lookup.
     * @param name  The name of the place to be looked up.
     */
    @JsonMethod("name")
    fun lookupplace(from: BasicProtocolActor, name: String) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getPlace(name, { obj: Any? ->
            val place = obj as PlaceDesc?
            val contextID = place?.contextID
            from.send(msgLookupPlace(this@AuthHandler, name, contextID))
        })
    }

    /**
     * Handle the 'setcansetpass' verb.
     *
     * Request that an actor's permission to change their password be changed.
     *
     * @param from  The administrator asking for the change.
     * @param id  The ID of the actor.
     * @param canSetPass  The (new) permission setting.
     */
    @JsonMethod("id", "cansetpass")
    fun setcansetpass(from: BasicProtocolActor, id: String, canSetPass: Boolean) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getActor(id, { obj: Any? ->
            if (obj != null) {
                val actor = obj as ActorDesc
                if (actor.canSetPass != canSetPass) {
                    actor.canSetPass = canSetPass
                    myAuthorizer.checkpointActor(actor)
                }
            }
        })
    }

    /**
     * Handle the 'setiid' verb.
     *
     * Request that an actor's internal ID be changed.
     *
     * @param from  The administrator asking for the change.
     * @param id  The ID of the actor.
     * @param optInternalID  The (new) internal ID for the actor (empty string or omitted
     * to remove).
     */
    @JsonMethod("id", "iid")
    fun setiid(from: BasicProtocolActor, id: String, optInternalID: OptString) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getActor(id, { obj: Any? ->
            if (obj != null) {
                val internalID = optInternalID.valueOrNull()
                val actor = obj as ActorDesc
                if (internalID != actor.internalID()) {
                    actor.setInternalID(internalID)
                    myAuthorizer.checkpointActor(actor)
                }
            }
        })
    }

    /**
     * Handle the 'setname' verb.
     *
     * Request that an actor's name be changed.
     *
     * @param from  The administrator asking for the name change.
     * @param id  The ID of the actor.
     * @param optName  The (new) name for the actor (empty string or omitted to
     * remove).
     */
    @JsonMethod("id", "name")
    fun setname(from: BasicProtocolActor, id: String, optName: OptString) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getActor(id, { obj: Any? ->
            if (obj != null) {
                val name = optName.valueOrNull()
                val actor = obj as ActorDesc
                if (name != actor.name) {
                    actor.setName(name)
                    myAuthorizer.checkpointActor(actor)
                }
            }
        })
    }

    /**
     * Handle the 'setpassword' verb.
     *
     * Request that an actor's password be changed.
     *
     * @param from  The administrator asking for the password change.
     * @param id  The ID of the actor.
     * @param optPassword  The (new) password for the actor (empty string or
     * omitted to remove).
     */
    @JsonMethod("id", "password")
    fun setpassword(from: BasicProtocolActor, id: String, optPassword: OptString) {
        myGatekeeper.ensureAuthorizedAdmin(from)
        myAuthorizer.getActor(id, { obj: Any? ->
            if (obj != null) {
                val password = optPassword.valueOrNull()
                val actor = obj as ActorDesc
                if (!actor.testPassword(password)) {
                    actor.setPassword(password)
                    myAuthorizer.checkpointActor(actor)
                }
            }
        })
    }
}