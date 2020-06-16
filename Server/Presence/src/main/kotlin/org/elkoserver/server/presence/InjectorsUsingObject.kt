package org.elkoserver.server.presence

import org.elkoserver.foundation.json.Injector

internal interface InjectorsUsingObject {
    fun setInjectors(injectors: Collection<Injector>)
}
