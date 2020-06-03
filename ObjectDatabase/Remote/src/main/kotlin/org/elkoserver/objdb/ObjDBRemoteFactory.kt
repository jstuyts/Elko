package org.elkoserver.objdb

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.net.NetworkManager
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.HostDescFromPropertiesFactory
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel
import java.time.Clock

class ObjDBRemoteFactory(
        private val myProps: ElkoProperties,
        private val objDbRemoteGorgel: Gorgel,
        val connectionRetrierWithoutLabelGorgel: Gorgel,
        val traceFactory: TraceFactory,
        val timer: Timer,
        val clock: Clock,
        val hostDescFromPropertiesFactory: HostDescFromPropertiesFactory,
        val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val getRequestFactory: GetRequestFactory,
        private val putRequestFactory: PutRequestFactory,
        private val updateRequestFactory: UpdateRequestFactory,
        private val queryRequestFactory: QueryRequestFactory,
        private val removeRequestFactory: RemoveRequestFactory) {
    fun create(serviceFinder: ServiceFinder, networkManager: NetworkManager, serverName: String, propRoot: String) =
            ObjDBRemote(
                    serviceFinder,
                    networkManager,
                    serverName,
                    myProps,
                    propRoot,
                    objDbRemoteGorgel,
                    connectionRetrierWithoutLabelGorgel,
                    traceFactory,
                    timer,
                    clock,
                    hostDescFromPropertiesFactory,
                    jsonToObjectDeserializer,
                    getRequestFactory,
                    putRequestFactory,
                    updateRequestFactory,
                    queryRequestFactory,
                    removeRequestFactory)
}
