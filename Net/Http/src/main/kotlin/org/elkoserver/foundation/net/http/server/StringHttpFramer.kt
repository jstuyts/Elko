package org.elkoserver.foundation.net.http.server

class StringHttpFramer private constructor() : HttpFramer() {
    /**
     * Return an iterator that will return the application-level message or
     * messages (if any) in the body of a received HTTP POST.
     *
     * @param postBody  The HTTP POST body in question.
     *
     * @return an iterator that can be called upon to return the application-
     * level message(s) contained within 'postBody'.
     */
    override fun postBodyUnpacker(postBody: String): Iterator<Any> = StringBodyUnpacker(postBody)

    /**
     * Post body unpacker for a plain string HTTP message.  In this case, the
     * HTTP POST body contains exactly one message, which is the value of
     * exactly one POSTed form field (whose name doesn't matter).
     *
     * @param postBody  The HTTP message body was POSTed.
     */
    private class StringBodyUnpacker internal constructor(postBody: String) : MutableIterator<Any> {
        /** The message string that was received.  */
        private var myReceivedMessage: String?

        /**
         * Test if there are more messages.
         *
         * When using this unpacking scheme, there can be only one
         * application- level message in an HTTP message.  That message has
         * either been given out or it hasn't.
         */
        override fun hasNext(): Boolean = myReceivedMessage != null

        /**
         * Get the next message.
         *
         * Since there is only the one application-level message possible
         * here, just return that, or null if it was returned previously.
         */
        override fun next(): String {
            val result = myReceivedMessage!!
            myReceivedMessage = null
            return result
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }

        init {
            var actualPostBody = postBody
            val junkMark = actualPostBody.indexOf('=')
            if (junkMark >= 0) {
                actualPostBody = actualPostBody.substring(junkMark + 1)
            }
            myReceivedMessage = actualPostBody
        }
    }
}
