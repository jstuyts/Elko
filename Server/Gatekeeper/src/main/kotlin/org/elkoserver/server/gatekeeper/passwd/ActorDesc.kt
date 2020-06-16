package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.json.RandomUsingObject
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory.type
import org.elkoserver.server.gatekeeper.MessageDigestUsingObject
import java.security.MessageDigest
import java.util.Random
import kotlin.experimental.and

/**
 * Database object describing an actor.
 */
class ActorDesc : Encodable, RandomUsingObject, MessageDigestUsingObject {
    /** The mandatory, invariant, unique, machine readable identifier.  */
    internal val id: String

    /** The optional, unique, internal use identifier.  */
    private var myInternalID: String?

    /** The optional, variable, non-unique, human readable identifier.  */
    internal var name: String?
        private set

    /** Password for login, or null if not password protected.  */
    private var myPassword: String? = null

    /** Salt for this actor's password.  */
    private var mySalt: ByteArray? = null

    /** Flag controlling permission for actor to modify their own password.  */
    internal var canSetPass: Boolean

    private lateinit var myRandom: Random

    private lateinit var myMessageDigest: MessageDigest

    /**
     * Normal constructor.
     *
     * @param id  The unique ID.
     * @param internalID  The internal ID, or null for none.
     * @param name  The human readable label.
     * @param password  Login password (plaintext), or null for none.
     * @param canSetPass  Permission to change password.
     */
    constructor(id: String, internalID: String?, name: String?, password: String?,
                canSetPass: Boolean, random: Random, messageDigest: MessageDigest) {
        this.id = id
        myInternalID = internalID
        this.name = name
        this.canSetPass = canSetPass
        myRandom = random
        myMessageDigest = messageDigest

        setPassword(password)
    }

    /**
     * JSON-driven constructor.
     *
     * @param id  The unique ID.
     * @param optInternalID  The internal ID.
     * @param optName  The human readable label.
     * @param optPassword  Login password (hashed).
     * @param optCanSetPass  Permission to change password.
     */
    @JSONMethod("id", "iid", "name", "password", "cansetpass")
    constructor(id: String, optInternalID: OptString, optName: OptString,
                optPassword: OptString, optCanSetPass: OptBoolean) {
        this.id = id
        myInternalID = optInternalID.value<String?>(null)
        name = optName.value<String?>(null)
        myPassword = optPassword.value<String?>(null)
        val currentPassword = myPassword
        if (currentPassword == null) {
            mySalt = null
        } else {
            val salt = ByteArray(4)
            mySalt = salt
            for (i in 0..3) {
                val frag = currentPassword.substring(i * 2, i * 2 + 2)
                salt[i] = (frag.toInt(16) and 0xFF).toByte()
            }
        }
        canSetPass = optCanSetPass.value(true)
    }

    override fun setRandom(random: Random) {
        myRandom = random
    }

    override fun setMessageDigest(messageDigest: MessageDigest) {
        myMessageDigest = messageDigest
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl) =
            type("actor", control).apply {
                addParameter("id", id)
                addParameterOpt("iid", myInternalID)
                addParameterOpt("name", name)
                addParameterOpt("password", myPassword)
                if (!canSetPass) {
                    addParameter("cansetpass", false)
                }
                finish()
            }

    /**
     * Compute a string that represents the SHA hash of a password string.
     *
     * @param salt  Random salt to impede dictionary attacks.
     * @param password  The password to hash.
     */
    private fun hashPassword(salt: ByteArray, password: String?): String {
        myMessageDigest.update(salt)
        myMessageDigest.update((password ?: "").toByteArray())
        val hash = myMessageDigest.digest()
        val encoded = CharArray(hash.size * 2 + 8)
        for (i in 0..3) {
            encoded[i * 2] = Integer.toHexString(salt[i].and(0xF0.toByte()).toInt() shr 4)[0]
            encoded[i * 2 + 1] = Integer.toHexString(salt[i].and(0x0F.toByte()).toInt())[0]
        }
        for (i in hash.indices) {
            encoded[i * 2 + 8] = Integer.toHexString(hash[i].and(0xF0.toByte()).toInt() shr 4)[0]
            encoded[i * 2 + 9] = Integer.toHexString(hash[i].and(0x0F.toByte()).toInt())[0]
        }
        return String(encoded)
    }

    /**
     * Get this actor's internal ID.
     *
     * @return this actor's internal ID.
     */
    fun internalID() = myInternalID ?: id

    /**
     * Set this actor's internal identifier.
     *
     * @param internalID  New internal ID.
     */
    fun setInternalID(internalID: String?) {
        myInternalID = if ("" == internalID) {
            null
        } else {
            internalID
        }
    }

    /**
     * Set this actor's name.
     *
     * @param name  New name.
     */
    fun setName(name: String?) {
        this.name = if ("" == name) {
            null
        } else {
            name
        }
    }

    /**
     * Set this actor's password.
     *
     * @param password  New password (null for no password).
     */
    fun setPassword(password: String?) {
        if (password == null) {
            mySalt = null
            myPassword = null
        } else {
            val salt = ByteArray(4)
            mySalt = salt
            myRandom.nextBytes(mySalt)
            myPassword = hashPassword(salt, password)
        }
    }

    /**
     * Test if a string matches this actor's password.
     *
     * @param password  String to test.
     *
     * @return true if 'password' matches this actor's password.
     */
    fun testPassword(password: String?): Boolean {
        return if (myPassword == null) {
            true
        } else {
            myPassword == hashPassword(mySalt!!, password ?: "")
        }
    }
}
