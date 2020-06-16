package org.elkoserver.server.presence

import org.elkoserver.foundation.json.Injector

internal class InjectorsInjector(private val injectors: List<Injector>) : Injector {
    override fun inject(any: Any?) {
        if (any is InjectorsUsingObject) {
            any.setInjectors(injectors)
        }
    }
}
