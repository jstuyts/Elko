package org.elkoserver.util.trace.slf4j

import org.slf4j.Logger

interface Gorgel : Logger {
    fun getChild(childName: String): Gorgel
}
