package org.elkoserver.server.gatekeeper

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.Server
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D

interface AuthorizerProvided {
    fun props(): D<ElkoProperties>

    fun gatekeeper(): D<Gatekeeper>

    fun server(): D<Server>

    fun baseCommGorgel(): D<Gorgel>

    fun objectDatabase(): D<ObjectDatabase>
}
