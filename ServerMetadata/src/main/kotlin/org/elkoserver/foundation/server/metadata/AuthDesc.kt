package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.json.JSONMethod
import org.elkoserver.foundation.json.OptString
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.json.Encodable
import org.elkoserver.json.EncodeControl
import org.elkoserver.json.JSONLiteralFactory
import org.elkoserver.util.trace.Trace

/**
 * Descriptor containing information required or presented to authorize a
 * connection.
 *
 * @param myMode  Authorization mode.
 * @param myCode  Authorization code, or null if not relevant.
 * @param myID  Authorization ID, or null if not relevant.
 */
class AuthDesc(private val myMode: String, private val myCode: String?, private val myID: String?) : Encodable {

    /**
     * JSON-driven constructor.
     *
     * @param mode  Authorization mode.
     * @param code  Optional authorization code.
     * @param id  Optional authorization ID.
     */
    @JSONMethod("mode", "code", "id")
    constructor(mode: String, code: OptString, id: OptString) : this(mode, code.value<String?>(null), id.value<String?>(null))

    /**
     * Get the authorization code.
     *
     * @return the authorization code (or null if there is none).
     */
    private fun code() = myCode

    /**
     * Get the authorization ID.
     *
     * @return the authorization ID (or null if there is none).
     */
    fun id() = myID

    /**
     * Get the authorization mode.
     *
     * @return the authorization mode.
     */
    fun mode() = myMode

    /**
     * Check an authorization.  This authorization descriptor is treated as a
     * set of requirements.  The authorization descriptor given in the 'auth'
     * parameter is treated as a presented set of authorization credentials.
     * The credentials are compared to the requirements to see if they satisfy
     * them.
     *
     * @param auth  Authorization credentials being presented.
     *
     * @return true if 'auth' correctly authorizes connection under
     * the authorization configuration described by this object.
     */
    fun verify(auth: AuthDesc?): Boolean {
        return when {
            auth == null -> myMode == "open"
            myMode == auth.mode() ->
                when (myMode) {
                    "open" -> true
                    "password" -> myCode == auth.code()
                    else -> false
                }
            else -> false
        }
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
            if (control.toClient() && myMode == "open") {
                null
            } else {
                val result = JSONLiteralFactory.type("auth", control)
                result.addParameter("mode", myMode)
                result.addParameterOpt("id", myID)
                result.addParameterOpt("code", myCode)
                result.finish()
                result
            }

    companion object {
        /** Singleton open authorization descriptor. This may be used in all
         * circumstances where open mode authorization is required or
         * presented.  */
        val theOpenAuth = AuthDesc("open", null, null)

        /**
         * Produce an AuthDesc object from information contained in the server
         * configuration properties.
         *
         * The authorization mode is extracted from propRoot+".auth.mode".
         * Currently, there are three possible authorization mode values that are
         * recognized: "open", "password", and "reservation".
         *
         * Open mode is unrestricted access.  No additional descriptive
         * information is required for open mode.
         *
         * Password mode requires a secret code string for access.  This code
         * string is extracted from propRoot+".auth.code".  Additionally, an
         * identifier may also be required, which will be extracted from
         * propRoot+".auth.id" if that property is present.
         *
         * Reservation mode requires a reservation string for access.  The
         * reservation string is communicated via a separate pathway, but it
         * optionally may be associated with an identifier extracted from
         * propRoot+".auth.id".
         *
         * @param props  The properties themselves.
         * @param propRoot  Prefix string for all the properties describing the
         * authorization information of interest.
         * @param appTrace  Trace object for error logging.
         *
         * @return an AuthDesc object constructed according to the properties
         * rooted at 'propRoot' as described above, or null if no such valid
         * authorization information could be found.
         */
        fun fromProperties(props: ElkoProperties, propRoot: String, appTrace: Trace): AuthDesc {
            val actualPropRoot = "$propRoot.auth"
            val mode = props.getProperty("$actualPropRoot.mode", "open")
            return if (mode == "open") {
                theOpenAuth
            } else {
                val code = props.getProperty<String?>("$actualPropRoot.code", null)
                if (mode == "password") {
                    if (code == null) {
                        appTrace.errorm("missing value for $actualPropRoot.code")
                        throw IllegalStateException()
                    }
                } else if (mode != "reservation") {
                    appTrace.errorm("unknown value for $actualPropRoot.auth.mode: $mode")
                    throw IllegalStateException()
                }
                val id = props.getProperty<String?>("$actualPropRoot.id", null)
                AuthDesc(mode, code, id)
            }
        }
    }
}
