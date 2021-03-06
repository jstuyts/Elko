package org.elkoserver.server.context.test

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.json.BaseCommGorgelInjector
import org.elkoserver.foundation.json.ClassspecificGorgelUsingObject
import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.Cryptor
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.PostInjectionInitializingObject
import org.elkoserver.foundation.net.Communication.COMMUNICATION_CATEGORY_TAG
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JsonDecodingException
import org.elkoserver.json.getRequiredInt
import org.elkoserver.json.getRequiredObject
import org.elkoserver.json.getRequiredString
import org.elkoserver.json.getStringOrNull
import org.elkoserver.server.context.Contextor
import org.elkoserver.server.context.EphemeralUserFactory
import org.elkoserver.server.context.model.User
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.GorgelImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.io.IOException
import java.time.Clock
import java.util.SortedSet
import java.util.TreeSet

/**
 * Test class for the user factory interface.
 *
 * This factory expects to be parameterized with a JSON object of the form:
 *
 * { blob: STRING }
 *
 * where the 'blob' STRING is a JSON object literal encrypted with a key that
 * this factory is configured with at construction time.  This object literal
 * in turn consists of:
 *
 * { nonce: STRING,
 * expire: INT,
 * context: CONTEXT_REF,
 * user: USER_OBJ }
 *
 * where:
 * 'nonce' is a random string token generated from a secure random source
 * 'expire' is the time (seconds since epoch) that the nonce expires
 * 'context' is the optional ref of the context into which the user will go
 * 'user' is a JSON encoded User object, as would be returned by the object
 * database
 *
 * Upon receiving one of these, if the current time is before the expiration
 * time and the nonce is not a previously received nonce, this factory will
 * construct and return the User object described by the 'user' property.  In
 * all other cases it will return null.
 *
 * Note that this factory will only keep track of unexpired nonces, and further
 * that we expect the expiration times on these to be relatively short (e.g.,
 * 30 seconds -- long enough to flow through the system in normal operation but
 * not long enough to be considered persistent by any standard and not long
 * enough for a large number of them to accumulate in this factory's history of
 * nonces seen).
 */
internal class TestUserFactory @JsonMethod("key") constructor(private val key: String) : EphemeralUserFactory, ClockUsingObject, ClassspecificGorgelUsingObject, PostInjectionInitializingObject {
    /** Cryptor incorporating the key used to decrypt blobs.  */
    private lateinit var myCryptor: Cryptor

    /** Collection of unexpired (or recently expired) nonces previously seen  */
    private val myNonces: SortedSet<Nonce> = TreeSet()

    /** Timestamp (seconds) we last removed expired nonces from myNonces  */
    private var myLastPurgeTime: Long = 0
    private lateinit var clock: Clock
    private lateinit var myGorgel: Gorgel
    private lateinit var jsonToObjectDeserializer: JsonToObjectDeserializer

    override fun setGorgel(gorgel: Gorgel) {
        myGorgel = gorgel
    }

    /**
     * Nonce.  Our nonces consist of an unguessable random string and an
     * expiration time.
     */
    private class Nonce(val expiration: Int, val nonceID: String) : Comparable<Nonce> {
        override fun compareTo(other: Nonce): Int {
            val primary = expiration - other.expiration
            return if (primary == 0) {
                nonceID.compareTo(other.nonceID)
            } else {
                primary
            }
        }

    }

    override fun setClock(clock: Clock) {
        this.clock = clock
        myLastPurgeTime = clock.millis() / 1000
    }

    override fun initialize() {
        jsonToObjectDeserializer = JsonToObjectDeserializer(
                GorgelImpl(LoggerFactory.getLogger(JsonToObjectDeserializer::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory()),
                GorgelImpl(LoggerFactory.getLogger(ConstructorInvoker::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory(), COMMUNICATION_CATEGORY_TAG),
                listOf(
                        ClockInjector(clock),
                        BaseCommGorgelInjector(GorgelImpl(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), LoggerFactory.getILoggerFactory(), MarkerFactory.getIMarkerFactory(), COMMUNICATION_CATEGORY_TAG))))
        myCryptor = Cryptor(key, GorgelImpl(LoggerFactory.getLogger(Cryptor::class.java),
                LoggerFactory.getILoggerFactory(),
                MarkerFactory.getIMarkerFactory()), jsonToObjectDeserializer)
    }

    /**
     * Synthesize a user object.
     *
     * @param contextor  The contextor of the server in which the synthetic
     * user will be present
     * @param connection  The connection over which the new user presented
     * themselves.
     * @param param  Arbitrary JSON object parameterizing the construction.
     * @param contextRef  Ref of context the new synthesized user will be
     * placed into
     * @param contextTemplate  Ref of the context template for the context
     *
     * @return a synthesized User object constructed according the
     * parameterized descriptor, or null if no such User object could be
     * produced for any reason.
     */
    override fun provideUser(contextor: Contextor, connection: Connection,
                             param: JsonObject?, contextRef: String,
                             contextTemplate: String?): User {
        try {
            val blob = param?.getRequiredString("blob") ?: throw IllegalStateException()
            val params = myCryptor.decryptJsonObject(blob)
            val userDesc = params.getRequiredObject("user")
            val expire = params.getRequiredInt("expire")
            val now = clock.millis() / 1000
            if (now < expire) {
                val nonceID = params.getRequiredString("nonce")
                val nonce = Nonce(expire, nonceID)
                if (!myNonces.contains(nonce)) {
                    purgeExpiredNonces(now)
                    myNonces.add(nonce)
                    val reqContextRef = params.getStringOrNull("context")
                    if (reqContextRef != null &&
                            reqContextRef != contextRef) {
                        myGorgel.error("context ref mismatch")
                        throw IllegalStateException()
                    }
                    val reqContextTemplate = params.getStringOrNull("ctmpl")
                    if (reqContextTemplate != null &&
                            reqContextTemplate != contextTemplate) {
                        myGorgel.error(
                                "context template ref mismatch")
                        throw IllegalStateException()
                    }
                    val result = jsonToObjectDeserializer.decode(User::class.java, userDesc, contextor.objectDatabase)
                    return result as User
                }
                myGorgel.error("reused nonce")
            } else {
                myGorgel.error("expired nonce")
            }
        } catch (e: IOException) {
            myGorgel.error("malformed cryptoblob")
        } catch (e: JsonParserException) {
            myGorgel.error("bad JSON string in cryptoblob")
        } catch (e: JsonDecodingException) {
            myGorgel.error("missing or improperly typed property in cryptoblob")
        }
        throw IllegalStateException()
    }

    /**
     * Scan through the collection of seen nonces and throw away the expired
     * ones, if it's been long enough since we last did that or if the
     * collection has grown too big.
     *
     * @param now  Time (seconds since epoch) that we are doing this
     */
    private fun purgeExpiredNonces(now: Long) {
        if (PURGE_TIME_THRESHOLD < now - myLastPurgeTime ||
            PURGE_SIZE_THRESHOLD < myNonces.size
        ) {
            myLastPurgeTime = now
            val iter = myNonces.iterator()
            while (iter.hasNext()) {
                val nonce = iter.next()
                if (nonce.expiration < now) {
                    iter.remove()
                } else {
                    break
                }
            }
        }
    }

    companion object {
        /** Time threshold (seconds) for triggering expired nonce purge  */
        private const val PURGE_TIME_THRESHOLD = 60

        /** Collection size threshold for triggering expired nonce purge  */
        private const val PURGE_SIZE_THRESHOLD = 1000
    }
}
