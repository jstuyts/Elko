package org.elkoserver.server.gatekeeper.passwd

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptBoolean
import org.elkoserver.foundation.json.OptString
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteral
import org.elkoserver.json.JSONLiteralFactory.type
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import kotlin.experimental.and

/**
 * Database object describing an actor.
 */
class ActorDesc : Encodable {
    /** The mandatory, invariant, unique, machine readable identifier.  */
    private var myID: String

    /** The optional, unique, internal use identifier.  */
    private var myInternalID: String?

    /** The optional, variable, non-unique, human readable identifier.  */
    private var myName: String?

    /** Password for login, or null if not password protected.  */
    private var myPassword: String? = null

    /** Salt for this actor's password.  */
    private var mySalt: ByteArray? = null

    /** Flag controlling permission for actor to modify their own password.  */
    private var myCanSetPass: Boolean

    companion object {
        /** Random number generator, for generating password salt.  */
        @Deprecated("Global variable")
        private val theRandom = SecureRandom()

        /** Object to SHA hash passwords.  */
        @Deprecated("Global variable")
        private val theSHA: MessageDigest

        init {
            theSHA = try {
                MessageDigest.getInstance("SHA")
            } catch (e: NoSuchAlgorithmException) {
                /* According to Sun's documentation, this exception can't actually
               happen, since the JVM is required to support the SHA algorithm.
               However, the compiler requires the catch.  And it *could* happen
               if either the documentation or the JVM implementation are wrong.
               Like that ever happens. */
                throw IllegalStateException("This JVM lacks SHA support", e)
            }
        }
    }

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
                canSetPass: Boolean) {
        myID = id
        myInternalID = internalID
        myName = name
        setPassword(password)
        myCanSetPass = canSetPass
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
        myID = id
        myInternalID = optInternalID.value<String?>(null)
        myName = optName.value<String?>(null)
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
        myCanSetPass = optCanSetPass.value(true)
    }

    /**
     * Test if this actor can set their own password.
     *
     * @return true if this actor can set their own password.
     */
    fun canSetPass(): Boolean {
        return myCanSetPass
    }

    /**
     * Encode this object for transmission or persistence.
     *
     * @param control  Encode control determining what flavor of encoding
     * should be done.
     *
     * @return a JSON literal representing this object.
     */
    override fun encode(control: EncodeControl): JSONLiteral {
        val result = type("actor", control)
        result.addParameter("id", myID)
        result.addParameterOpt("iid", myInternalID)
        result.addParameterOpt("name", myName)
        result.addParameterOpt("password", myPassword)
        if (!myCanSetPass) {
            result.addParameter("cansetpass", false)
        }
        result.finish()
        return result
    }

    /**
     * Compute a string that represents the SHA hash of a password string.
     *
     * @param salt  Random salt to impede dictionary attacks.
     * @param password  The password to hash.
     */
    private fun hashPassword(salt: ByteArray, password: String?): String {
        theSHA.update(salt)
        theSHA.update((password ?: "").toByteArray())
        val hash = theSHA.digest()
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
     * Get this actor's unique ID.
     *
     * @return this actor's unique ID.
     */
    fun id(): String {
        return myID
    }

    /**
     * Get this actor's internal ID.
     *
     * @return this actor's internal ID.
     */
    fun internalID(): String? {
        return if (myInternalID != null) {
            myInternalID
        } else {
            myID
        }
    }

    /**
     * Get this actor's human-readable label.
     *
     * @return this actor's human-readable label.
     */
    fun name(): String? {
        return myName
    }

    /**
     * Set this actor's permission to change their password.
     *
     * @param canSetPass  true if actor can change their password, false if not
     */
    fun setCanSetPass(canSetPass: Boolean) {
        myCanSetPass = canSetPass
    }

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
        myName = if ("" == name) {
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
            theRandom.nextBytes(mySalt)
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