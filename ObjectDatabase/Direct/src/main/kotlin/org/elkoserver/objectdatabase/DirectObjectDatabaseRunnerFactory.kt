package org.elkoserver.objectdatabase

import org.elkoserver.foundation.run.singlethreadexecutor.SingleThreadExecutorRunner

class DirectObjectDatabaseRunnerFactory {
    fun create() = SingleThreadExecutorRunner("Elko RunQueue DirectObjectDatabase")
}
