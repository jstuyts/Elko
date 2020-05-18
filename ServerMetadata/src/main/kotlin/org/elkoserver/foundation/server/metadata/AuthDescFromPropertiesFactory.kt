package org.elkoserver.foundation.server.metadata

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel

class AuthDescFromPropertiesFactory(private val props: ElkoProperties, private val gorgel: Gorgel) {

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
     * @param propRoot  Prefix string for all the properties describing the
     * authorization information of interest.
     *
     * @return an AuthDesc object constructed according to the properties
     * rooted at 'propRoot' as described above, or null if no such valid
     * authorization information could be found.
     */
    fun fromProperties(propRoot: String): AuthDesc {
        val actualPropRoot = "$propRoot.auth"
        val mode = props.getProperty("$actualPropRoot.mode", "open")
        return if (mode == "open") {
            AuthDesc.theOpenAuth
        } else {
            val code = props.getProperty<String?>("$actualPropRoot.code", null)
            if (mode == "password") {
                if (code == null) {
                    gorgel.error("missing value for $actualPropRoot.code")
                    throw IllegalStateException()
                }
            } else if (mode != "reservation") {
                gorgel.error("unknown value for $actualPropRoot.auth.mode: $mode")
                throw IllegalStateException()
            }
            val id = props.getProperty<String?>("$actualPropRoot.id", null)
            AuthDesc(mode, code, id)
        }
    }
}
