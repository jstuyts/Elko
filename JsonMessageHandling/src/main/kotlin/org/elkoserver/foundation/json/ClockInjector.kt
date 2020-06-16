package org.elkoserver.foundation.json

import java.time.Clock

class ClockInjector(private val clock: Clock) : Injector {
    override fun inject(any: Any?) {
        if (any is ClockUsingObject) {
            any.setClock(clock)
        }
    }
}
