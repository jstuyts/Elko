package org.elkoserver.foundation.net

/**
 * Holder for the request headers requested to be allowed by an HTTP OPTIONS
 * request, so that we can later unconditionally allow them.  Geeze.
 *
 * @param request  The HTTP OPTIONS request of interest, requesting
 *     cross-site resource access.
 */
class HTTPOptionsReply(request: HTTPRequest) {
    private val myHeader = request.header("access-control-request-headers")

    /**
     * Generate the Access-Control-Allow-Headers header of the reply to the
     * OPTIONS request, allowing the requestor whatever access they asked for.
     */
    fun headersHeader() = if (myHeader != null) "Access-Control-Allow-Headers: $myHeader\r\n" else ""
}
