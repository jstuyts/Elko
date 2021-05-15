package org.elkoserver.objectdatabase

import org.elkoserver.foundation.run.thread.ThreadRunner
import org.elkoserver.util.trace.slf4j.Gorgel

class DirectObjectDatabaseRunnerFactory(private val runnerGorgel: Gorgel) {
    fun create() = ThreadRunner("Elko RunQueue DirectObjectDatabase", runnerGorgel)
}
