package org.elkoserver.util.trace.slf4j

class Tag(val key: String, val value: String) {
    internal val markerName = "$key:$value"
}
