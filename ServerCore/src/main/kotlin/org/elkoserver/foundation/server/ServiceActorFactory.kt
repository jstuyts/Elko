package org.elkoserver.foundation.server

import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.util.trace.slf4j.Gorgel

class ServiceActorFactory(
        private val gorgel: Gorgel,
        private val commGorgel: Gorgel,
        private val mustSendDebugReplies: Boolean) {
    fun create(connection: Connection, refTable: RefTable, desc: ServiceDesc, server: Server): ServiceActor =
            ServiceActor(connection, refTable, desc, server, gorgel, commGorgel, mustSendDebugReplies)
}
