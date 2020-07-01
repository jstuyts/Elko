package org.elkoserver.server.context.test

import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.json.ClockInjector
import org.elkoserver.foundation.json.ClockUsingObject
import org.elkoserver.foundation.json.ConstructorInvoker
import org.elkoserver.foundation.json.Cryptor
import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.json.PostInjectionInitializingObject
import org.elkoserver.foundation.json.TraceFactoryInjector
import org.elkoserver.foundation.json.TraceFactoryUsingObject
import org.elkoserver.foundation.net.Connection
import org.elkoserver.json.JSONDecodingException
import org.elkoserver.json.JsonObject
import org.elkoserver.server.context.Contextor
import org.elkoserver.server.context.EphemeralUserFactory
import org.elkoserver.server.context.User
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.GorgelImpl
import org.elkoserver.util.trace.slf4j.Tag
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
 * 'user' is a JSON encoded User object, as would be returned by the ODB
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
internal class TestUserFactory @JSONMethod("key") constructor(private val key: String) : EphemeralUserFactory, ClockUsingObject, TraceFactoryUsingObject, PostInjectionInitializingObject {
    /** Cryptor incorporating the key used to decrypt blobs.  */
    private var myCryptor: Cryptor? = null

    /** Collection of unexpired (or recently expired) nonces previously seen  */
    private val myNonces: SortedSet<Nonce> = TreeSet()

    /** Timestamp (seconds) we last removed expired nonces from myNonces  */
    private var myLastPurgeTime: Long = 0
    private lateinit var traceFactory: TraceFactory
    private lateinit var clock: Clock
    private lateinit var jsonToObjectDeserializer: JsonToObjectDeserializer

    /**
     * Nonce.  Our nonces consist of an unguessable random string and an
     * expiration time.
     */
    private class Nonce internal constructor(val expiration: Int, val nonceID: String) : Comparable<Nonce> {
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

    override fun setTraceFactory(traceFactory: TraceFactory) {
        this.traceFactory = traceFactory
    }

    override fun initialize() {
        jsonToObjectDeserializer = JsonToObjectDeserializer(
                GorgelImpl(LoggerFactory.getLogger(JsonToObjectDeserializer::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory()),
                GorgelImpl(LoggerFactory.getLogger(ConstructorInvoker::class.java),
                        LoggerFactory.getILoggerFactory(),
                        MarkerFactory.getIMarkerFactory(), Tag("category", "comm")),
                listOf(ClockInjector(clock), TraceFactoryInjector(traceFactory)))
        myCryptor = Cryptor(key, traceFactory.trace("cryptor"), jsonToObjectDeserializer)
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
        if (myCryptor != null) {
            try {
                val blob = param!!.getString("blob")
                val params = myCryptor!!.decryptJSONObject(blob)
                val userDesc = params.getObject("user")
                val expire = params.getInt("expire")
                val now = clock.millis() / 1000
                if (expire > now) {
                    val nonceID = params.getString("nonce")
                    val nonce = Nonce(expire, nonceID)
                    if (!myNonces.contains(nonce)) {
                        purgeExpiredNonces(now)
                        myNonces.add(nonce)
                        val reqContextRef = params.getString<String?>("context", null)
                        if (reqContextRef != null &&
                                reqContextRef != contextRef) {
                            contextor.tr.errorm("context ref mismatch")
                            throw IllegalStateException()
                        }
                        val reqContextTemplate = params.getString<String?>("ctmpl", null)
                        if (reqContextTemplate != null &&
                                reqContextTemplate != contextTemplate) {
                            contextor.tr.errorm(
                                    "context template ref mismatch")
                            throw IllegalStateException()
                        }
                        val result = jsonToObjectDeserializer.decode(User::class.java, userDesc, contextor.odb)
                        return result as User
                    }
                    contextor.tr.errorm("reused nonce")
                } else {
                    contextor.tr.errorm("expired nonce")
                }
            } catch (e: IOException) {
                contextor.tr.errorm("malformed cryptoblob")
            } catch (e: JsonParserException) {
                contextor.tr.errorm("bad JSON string in cryptoblob")
            } catch (e: JSONDecodingException) {
                contextor.tr.errorm("missing or improperly typed property in cryptoblob")
            }
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
        if (now - myLastPurgeTime > PURGE_TIME_THRESHOLD ||
                myNonces.size > PURGE_SIZE_THRESHOLD) {
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
