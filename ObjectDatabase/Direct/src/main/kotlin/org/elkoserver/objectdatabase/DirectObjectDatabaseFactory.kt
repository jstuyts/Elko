package org.elkoserver.objectdatabase

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.slf4j.Gorgel

class DirectObjectDatabaseFactory(
        private val props: ElkoProperties,
        private val gorgel: Gorgel,
        private val runnerFactory: DirectObjectDatabaseRunnerFactory,
        private val baseGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Runner) {
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
