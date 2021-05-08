package org.elkoserver.objectdatabase.store.filestore

import org.elkoserver.util.trace.slf4j.GorgelImpl
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

val GORGEL = GorgelImpl(LoggerFactory.getLogger("test"), LoggerFactory.getILoggerFactory(), MarkerFactory.getIMarkerFactory())

const val SOME_BOOLEAN = false

const val OBJECT_FILE_CONTENTS = """{"property": "value"}"""

fun objectWithPropertyRef(referencedRef: String) = """{"ref${'$'}property": "$referencedRef"}"""

fun objectWithNullPropertyRef() = """{"ref${'$'}property": null}"""

fun objectWithNonStringPropertyRef() = """{"ref${'$'}property": 0}"""

fun objectWithArrayElementRef(referencedRef: String) = """{"ref${'$'}array": [ "$referencedRef" ]}"""

fun objectWithEmptyArrayElementRef() = """{"ref${'$'}array": []}"""

fun objectWithNullArrayElementRef() = """{"ref${'$'}array": [ null ]}"""

fun objectWithNonStringArrayElementRef() = """{"ref${'$'}array": [ 0 ]}"""
