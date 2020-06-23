package org.elkoserver.objdb

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.TraceFactory
import org.elkoserver.util.trace.slf4j.Gorgel

class ObjDBLocalFactory(
        private val props: ElkoProperties,
        private val gorgel: Gorgel,
        private val baseGorgel: Gorgel,
        private val traceFactory: TraceFactory,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Runner) {
    fun create(propRoot: String) =
            ObjDBLocal(
                    props,
                    propRoot,
                    gorgel,
                    baseGorgel,
                    traceFactory,
                    jsonToObjectDeserializer,
                    returnRunner)
}
