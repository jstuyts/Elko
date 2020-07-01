package org.elkoserver.foundation.json

import org.elkoserver.util.trace.slf4j.Gorgel

class ClassspecificGorgelInjector(private val baseGorgel: Gorgel) : Injector {
    override fun inject(any: Any?) {
        if (any is ClassspecificGorgelUsingObject) {
            any.setGorgel(baseGorgel.getChild(any::class))
        }
    }
}
