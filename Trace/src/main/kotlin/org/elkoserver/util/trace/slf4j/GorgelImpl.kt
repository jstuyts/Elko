package org.elkoserver.util.trace.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.reflect.KClass

class GorgelImpl(logger: Logger, private val loggerFactory: ILoggerFactory, private val markerFactory: IMarkerFactory, staticTags: Marker?) : Gorgel(logger, staticTags) {

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory) : this(logger, loggerFactory, markerFactory, null)

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, tag: String) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(tag))

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, firstTag: String, secondTag: String) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(firstTag, secondTag))

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, vararg tags: String) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(*tags))

    override fun tags(tag: String) = combineWithStaticTags(tag)

    override fun tags(firstTag: String, secondTag: String) = combineWithStaticTags(firstTag, secondTag)

    override fun tags(vararg tags: String) = combineWithStaticTags(*tags)

    override fun withAdditionalStaticTags(tag: String) =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(tag))

    override fun withAdditionalStaticTags(firstTag: String, secondTag: String) =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(firstTag))

    override fun withAdditionalStaticTags(vararg tags: String) =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(*tags))

    override fun getChild(childName: String) =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, staticTags)

    override fun getChild(theClass: KClass<*>) = getChild(requireNotNull(theClass.qualifiedName))

    override fun getChild(childName: String, tag: String) =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(tag))

    override fun getChild(theClass: KClass<*>, tag: String) =
            getChild(requireNotNull(theClass.qualifiedName), tag)

    override fun getChild(childName: String, firstTag: String, secondTag: String) =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(firstTag, secondTag))

    override fun getChild(theClass: KClass<*>, firstTag: String, secondTag: String) =
            getChild(requireNotNull(theClass.qualifiedName), firstTag, secondTag)

    override fun getChild(childName: String, vararg tags: String) =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(*tags))

    override fun getChild(theClass: KClass<*>, vararg tags: String) =
            getChild(requireNotNull(theClass.qualifiedName), *tags)

    private fun getFullyQualifiedChildName(childName: String) =
            if (Logger.ROOT_LOGGER_NAME == logger.name) childName else "${logger.name}.$childName"

    private fun combineWithStaticTags(tag: String) = markerFactory.getDetachedMarker(tag).addStaticTagsIfPresent()

    private fun combineWithStaticTags(firstTag: String, secondTag: String) =
            markerFactory.getDetachedMarker(firstTag, secondTag).addStaticTagsIfPresent()

    private fun combineWithStaticTags(vararg tags: String) =
            markerFactory.getDetachedMarker(*tags).addStaticTagsIfPresent()

    private fun Marker.addStaticTagsIfPresent(): Marker {
        staticTags?.let { add(it) }
        return this
    }
}

private fun IMarkerFactory.getDetachedMarker(firstTag: String, secondTag: String) =
        getDetachedMarker(firstTag).also {
            it.add(getDetachedMarker(secondTag))
        }

private fun IMarkerFactory.getDetachedMarker(vararg tags: String) =
        getDetachedMarker(tags[0]).also {
            for (index in 1 until tags.size) {
                it.add(getDetachedMarker(tags[index]))
            }
        }