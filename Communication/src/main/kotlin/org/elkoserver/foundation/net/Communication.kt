package org.elkoserver.foundation.net

import org.elkoserver.util.trace.slf4j.Tag

/**
 * Manage network connections between this server and other entities.
 */
object Communication {
    /** Maximum length message that a connection will be able to receive.  */
    const val MAX_MSG_LENGTH = 1024 * 1024

    val COMMUNICATION_CATEGORY_TAG = Tag("category", "comm")
}
