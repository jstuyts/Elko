package org.elkoserver.foundation.json

import org.elkoserver.util.trace.TraceFactory


class TraceFactoryInjector(private val traceFactory: TraceFactory) : Injector {
    override fun inject(any: Any?) {
        if (any is TraceFactoryUsingObject) {
            any.setTraceFactory(traceFactory)
        }
    }
}
