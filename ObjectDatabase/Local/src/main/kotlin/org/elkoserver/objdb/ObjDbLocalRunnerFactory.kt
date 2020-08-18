package org.elkoserver.objdb

import org.elkoserver.foundation.run.thread.ThreadRunner
import org.elkoserver.util.trace.slf4j.Gorgel

class ObjDbLocalRunnerFactory(private val runnerGorgel: Gorgel) {
    fun create() = ThreadRunner("Elko RunQueue LocalObjDb", runnerGorgel)
}
