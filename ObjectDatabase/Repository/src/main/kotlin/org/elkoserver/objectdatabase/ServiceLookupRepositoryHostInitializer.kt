package org.elkoserver.objectdatabase

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.server.metadata.ServiceDesc
import org.elkoserver.foundation.server.metadata.ServiceFinder
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.function.Consumer

class ServiceLookupRepositoryHostInitializer(
    private val props: ElkoProperties,
    private val odbPropRoot: String,
    private val serviceName: String,
    private val serviceFinder: ServiceFinder,
    private val gorgel: Gorgel
) : RepositoryHostInitializer {
    override fun initialize(objectDatabaseRepository: ObjectDatabaseRepository) {
        val actualServiceName = if (serviceName == "any") "repository-rep" else "repository-rep-$serviceName"
        val myRetryInterval = props.intProperty("$odbPropRoot.retry", -1)
        serviceFinder.findService(actualServiceName,
            RepositoryFoundHandler(objectDatabaseRepository, myRetryInterval), false)
    }

    private inner class RepositoryFoundHandler(
        private val objectDatabaseRepository: ObjectDatabaseRepository,
        private val myRetryInterval: Int
    ) : Consumer<Array<ServiceDesc>> {
        override fun accept(obj: Array<ServiceDesc>) {
            if (obj[0].failure != null) {
                gorgel.error("unable to find repository: ${obj[0].failure}")
            } else {
                objectDatabaseRepository.connectToRepository(obj[0].asHostDesc(myRetryInterval))
            }
        }
    }
}