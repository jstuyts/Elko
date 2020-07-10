package org.elkoserver.objdb

import org.elkoserver.foundation.run.Runner
import org.elkoserver.util.trace.slf4j.Gorgel

class ObjDbLocalRunnerFactory(private val runnerGorgel: Gorgel) {
    fun create() = Runner("Elko RunQueue LocalObjDb", runnerGorgel)
}
