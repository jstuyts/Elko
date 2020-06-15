package org.elkoserver.server.presence

import org.elkoserver.foundation.json.Injector

internal class DomainRegistryInjector(private val domainRegistry: DomainRegistry) : Injector {
    override fun inject(any: Any?) {
        if (any is DomainRegistryUsingObject) {
            any.setDomainRegistry(domainRegistry)
        }
    }
}