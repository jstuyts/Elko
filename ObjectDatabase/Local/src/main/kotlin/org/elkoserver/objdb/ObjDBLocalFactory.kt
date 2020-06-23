package org.elkoserver.objdb

import org.elkoserver.foundation.json.JsonToObjectDeserializer
import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.slf4j.Gorgel

class ObjDBLocalFactory(
        private val props: ElkoProperties,
        private val gorgel: Gorgel,
        private val runnerGorgel: Gorgel,
        private val baseGorgel: Gorgel,
        private val jsonToObjectDeserializer: JsonToObjectDeserializer,
        private val returnRunner: Runner) {
    fun create(propRoot: String) =
            ObjDBLocal(
                    props,
                    propRoot,
                    gorgel,
                    baseGorgel,
                    jsonToObjectDeserializer,
                    Runner("Elko RunQueue LocalObjDB", runnerGorgel),
                    returnRunner)
}
