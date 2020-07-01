package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ProvidedBase

interface AuthorizerProvided : ProvidedBase {
    fun props(): D<ElkoProperties>

    fun gatekeeper(): D<Gatekeeper>

    fun server(): D<Server>

    fun baseGorgel(): D<Gorgel>

    fun baseCommGorgel(): D<Gorgel>
}
