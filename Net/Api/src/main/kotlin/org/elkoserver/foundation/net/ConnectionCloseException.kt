package org.elkoserver.foundation.net

import java.io.IOException

/**
 * Exception to report that a connection was shutdown normally (i.e., without
 * error).
 */
class ConnectionCloseException(msg: String?) : IOException(msg) 