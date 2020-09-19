package org.elkoserver.foundation.json

import org.elkoserver.foundation.run.SlowServiceRunner

class SlowServiceRunnerInjector(private val slowServiceRunner: SlowServiceRunner) : Injector {
    override fun inject(any: Any?) {
        if (any is SlowServiceRunnerUsingObject) {
            any.setSlowServiceRunner(slowServiceRunner)
        }
    }
}
