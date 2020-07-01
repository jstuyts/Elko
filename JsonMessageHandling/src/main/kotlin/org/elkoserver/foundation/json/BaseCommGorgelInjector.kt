package org.elkoserver.foundation.json

import org.elkoserver.util.trace.slf4j.Gorgel

class BaseCommGorgelInjector(private val baseCommGorgel: Gorgel) : Injector {
    override fun inject(any: Any?) {
        if (any is BaseCommGorgelUsingObject) {
            any.setBaseCommGorgel(baseCommGorgel)
        }
    }
}
