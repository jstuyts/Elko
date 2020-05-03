package org.elkoserver.util.trace.slf4j

import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker

class GorgelImpl(logger: Logger, private val markerFactory: IMarkerFactory, staticTags: Marker?) : Gorgel(logger, staticTags) {

    constructor(logger: Logger, markerFactory: IMarkerFactory) : this(logger, markerFactory, null)

    constructor(logger: Logger, markerFactory: IMarkerFactory, tag: String) : this(logger, markerFactory, markerFactory.getDetachedMarker(tag))

    constructor(logger: Logger, markerFactory: IMarkerFactory, firstTag: String, secondTag: String) : this(logger, markerFactory, markerFactory.getDetachedMarker(firstTag, secondTag))

    constructor(logger: Logger, markerFactory: IMarkerFactory, vararg tags: String) : this(logger, markerFactory, markerFactory.getDetachedMarker(*tags))

    override fun tags(tag: String) = combineWithStaticTags(tag)

    override fun tags(firstTag: String, secondTag: String) = combineWithStaticTags(firstTag, secondTag)

    override fun tags(vararg tags: String) = combineWithStaticTags(*tags)

    override fun withAdditionalStaticTags(tag: String) =
            GorgelImpl(logger, markerFactory, combineWithStaticTags(tag))

    override fun withAdditionalStaticTags(firstTag: String, secondTag: String) =
            GorgelImpl(logger, markerFactory, combineWithStaticTags(firstTag))

    override fun withAdditionalStaticTags(vararg tags: String) =
            GorgelImpl(logger, markerFactory, combineWithStaticTags(*tags))

    override fun getChild(childName: String) =
            GorgelImpl(LoggerFactory.getLogger(getFullyQualifiedChildName(childName)), markerFactory, staticTags)

    override fun getChild(childName: String, tag: String) =
            GorgelImpl(LoggerFactory.getLogger(getFullyQualifiedChildName(childName)), markerFactory, combineWithStaticTags(tag))

    override fun getChild(childName: String, firstTag: String, secondTag: String) =
            GorgelImpl(LoggerFactory.getLogger(getFullyQualifiedChildName(childName)), markerFactory, combineWithStaticTags(firstTag, secondTag))

    override fun getChild(childName: String, vararg tags: String) =
            GorgelImpl(LoggerFactory.getLogger(getFullyQualifiedChildName(childName)), markerFactory, combineWithStaticTags(*tags))

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
