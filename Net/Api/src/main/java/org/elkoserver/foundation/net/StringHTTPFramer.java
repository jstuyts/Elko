package org.elkoserver.foundation.net;

import org.elkoserver.util.trace.Trace;

import java.util.Iterator;

public class StringHTTPFramer extends HTTPFramer {
    /**
     * Constructor.
     *
     * @param msgTrace Trace object for logging message traffic.
     */
    protected StringHTTPFramer(Trace msgTrace) {
        super(msgTrace);
    }

    /**
     * Return an iterator that will return the application-level message or
     * messages (if any) in the body of a received HTTP POST.
     *
     * @param postBody  The HTTP POST body in question.
     *
     * @return an iterator that can be called upon to return the application-
     *    level message(s) contained within 'postBody'.
     */
    public Iterator<Object> postBodyUnpacker(String postBody) {
        return new StringBodyUnpacker(postBody);
    }

    /**
     * Post body unpacker for a plain string HTTP message.  In this case, the
     * HTTP POST body contains exactly one message, which is the value of
     * exactly one POSTed form field (whose name doesn't matter).
     */
    private static class StringBodyUnpacker implements Iterator<Object> {
        /** The message string that was received. */
        private String myReceivedMessage;

        /**
         * Constructor.  Just strip the form variable name and remember the
         * rest.
         *
         * @param postBody  The HTTP message body was POSTed.
         */
        StringBodyUnpacker(String postBody) {
            int junkMark = postBody.indexOf('=');
            if (junkMark >= 0) {
                postBody = postBody.substring(junkMark + 1);
            }
            myReceivedMessage = postBody;
        }

        /**
         * Test if there are more messages.
         *
         * <p>When using this unpacking scheme, there can be only one
         * application- level message in an HTTP message.  That message has
         * either been given out or it hasn't.
         */
        public boolean hasNext() {
            return myReceivedMessage != null;
        }

        /**
         * Get the next message.
         *
         * <p>Since there is only the one application-level message possible
         * here, just return that, or null if it was returned previously.
         */
        public String next() {
            String result = myReceivedMessage;
            myReceivedMessage = null;
            return result;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
