package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import java.util.concurrent.Executor

class DirectObjectDatabaseFactory(
        private val props: ElkoProperties,
        private val propRoot: String,
        private val gorgel: Gorgel,
        private val runnerFactory: DirectObjectDatabaseRunnerFactory,
        private val baseGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Executor): ObjectDatabaseFactory {
    override fun create() =
        ObjectDatabaseDirect(
            props,
            propRoot,
            gorgel,
            baseGorgel,
            jsonToObjectDeserializer,
            runnerFactory.create(),
            returnRunner)
}
