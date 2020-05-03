package org.elkoserver.util.trace.slf4j

import org.slf4j.Logger
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory

class GorgelImpl(private val logger: Logger) : Gorgel, Logger by logger {
    override fun getChild(childName: String): GorgelImpl {
        val fullyQualifiedChildName = if (ROOT_LOGGER_NAME == name) childName else "${name}.$childName"
        return GorgelImpl(LoggerFactory.getLogger(fullyQualifiedChildName))
    }
}
