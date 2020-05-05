package org.elkoserver.foundation.net

/**
 * Interface implemented by message objects that have resources that need to
 * be released after the message has been transmitted.
 */
internal interface Releasable {
    /** Release any resources associated with this object.  */
    fun release()
}