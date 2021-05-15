package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.concurrent.Executor

class DirectObjectDatabaseFactory(
        private val props: ElkoProperties,
        private val gorgel: Gorgel,
        private val runnerFactory: DirectObjectDatabaseRunnerFactory,
        private val baseGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Executor) {
    fun create(propRoot: String): ObjectDatabaseDirect =
            ObjectDatabaseDirect(
                    props,
                    propRoot,
                    gorgel,
                    baseGorgel,
                    jsonToObjectDeserializer,
                    runnerFactory.create(),
                    returnRunner)
}
