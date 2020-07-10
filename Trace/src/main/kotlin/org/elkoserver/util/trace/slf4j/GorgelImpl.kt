package org.elkoserver.util.trace.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import kotlin.reflect.KClass

class GorgelImpl(logger: Logger, private val loggerFactory: ILoggerFactory, private val markerFactory: IMarkerFactory, staticTags: Marker?) : Gorgel(logger, staticTags) {

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory) : this(logger, loggerFactory, markerFactory, null)

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, tag: Tag) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(tag))

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, firstTag: Tag, secondTag: Tag) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(firstTag, secondTag))

    constructor(logger: Logger, loggerFactory: ILoggerFactory, markerFactory: IMarkerFactory, vararg tags: Tag) : this(logger, loggerFactory, markerFactory, markerFactory.getDetachedMarker(*tags))

    override fun tags(tag: Tag): Marker = combineWithStaticTags(tag)

    override fun tags(firstTag: Tag, secondTag: Tag): Marker = combineWithStaticTags(firstTag, secondTag)

    override fun tags(vararg tags: Tag): Marker = combineWithStaticTags(*tags)

    override fun withAdditionalStaticTags(tag: Tag): GorgelImpl =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(tag))

    override fun withAdditionalStaticTags(firstTag: Tag, secondTag: Tag): GorgelImpl =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(firstTag))

    override fun withAdditionalStaticTags(vararg tags: Tag): GorgelImpl =
            GorgelImpl(logger, loggerFactory, markerFactory, combineWithStaticTags(*tags))

    override fun getChild(childName: String): GorgelImpl =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, staticTags)

    override fun getChild(theClass: KClass<*>): GorgelImpl = getChild(requireNotNull(theClass.qualifiedName))

    override fun getChild(childName: String, tag: Tag): GorgelImpl =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(tag))

    override fun getChild(theClass: KClass<*>, tag: Tag): GorgelImpl =
            getChild(requireNotNull(theClass.qualifiedName), tag)

    override fun getChild(childName: String, firstTag: Tag, secondTag: Tag): GorgelImpl =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(firstTag, secondTag))

    override fun getChild(theClass: KClass<*>, firstTag: Tag, secondTag: Tag): GorgelImpl =
            getChild(requireNotNull(theClass.qualifiedName), firstTag, secondTag)

    override fun getChild(childName: String, vararg tags: Tag): GorgelImpl =
            GorgelImpl(loggerFactory.getLogger(getFullyQualifiedChildName(childName)), loggerFactory, markerFactory, combineWithStaticTags(*tags))

    override fun getChild(theClass: KClass<*>, vararg tags: Tag): GorgelImpl =
            getChild(requireNotNull(theClass.qualifiedName), *tags)

    private fun getFullyQualifiedChildName(childName: String) =
            if (Logger.ROOT_LOGGER_NAME == logger.name) childName else "${logger.name}.$childName"

    private fun combineWithStaticTags(tag: Tag) = markerFactory.getDetachedMarker(tag).addStaticTagsIfPresent()

    private fun combineWithStaticTags(firstTag: Tag, secondTag: Tag) =
            markerFactory.getDetachedMarker(firstTag, secondTag).addStaticTagsIfPresent()

    private fun combineWithStaticTags(vararg tags: Tag) =
            markerFactory.getDetachedMarker(*tags).addStaticTagsIfPresent()

    private fun Marker.addStaticTagsIfPresent(): Marker {
        staticTags?.let(this::add)
        return this
    }
}

private fun IMarkerFactory.getDetachedMarker(firstTag: Tag, secondTag: Tag) =
        getDetachedMarker(firstTag).also {
            it.add(getDetachedMarker(secondTag))
        }

private fun IMarkerFactory.getDetachedMarker(vararg tags: Tag) =
        getDetachedMarker(tags[0].markerName).also {
            for (index in 1 until tags.size) {
                it.add(getDetachedMarker(tags[index].markerName))
            }
        }
