package org.elkoserver.objdb

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.slf4j.Gorgel

class ObjDbLocalFactory(
        private val props: ElkoProperties,
        private val gorgel: Gorgel,
        private val runnerFactory: ObjDbLocalRunnerFactory,
        private val baseGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Runner) {
    fun create(propRoot: String): ObjDbLocal =
            ObjDbLocal(
                    props,
                    propRoot,
                    gorgel,
                    baseGorgel,
                    jsonToObjectDeserializer,
                    runnerFactory.create(),
                    returnRunner)
}
