package org.elkoserver.util.trace

interface TraceMessageAcceptor {
    fun accept(message: TraceMessage)

    fun setConfiguration(name: String, value: String)

    /**
     * After this call, the <tt>TraceMessageAcceptor</tt> must obey settings
     * from the environment.  Before this call, it must defer taking any
     * visible action, because it can't yet know what action is
     * appropriate.  Note that the message acceptor may (is encouraged to)
     * accept messages before setup is complete, because some of those trace
     * messages might be useful.
     *
     * It is an error to call this method more than once.
     */
    fun setupIsComplete()
}
