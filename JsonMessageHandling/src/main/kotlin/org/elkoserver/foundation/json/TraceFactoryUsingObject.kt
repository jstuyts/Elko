package org.elkoserver.foundation.json

import org.elkoserver.util.trace.TraceFactory

interface TraceFactoryUsingObject {
    fun setTraceFactory(traceFactory: TraceFactory)
}