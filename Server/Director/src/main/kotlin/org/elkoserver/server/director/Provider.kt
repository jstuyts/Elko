package org.elkoserver.server.director

import org.elkoserver.ordinalgeneration.OrdinalGenerator
import org.elkoserver.util.HashMapMultiImpl
import org.elkoserver.util.trace.slf4j.Gorgel

/**
 * The provider facet of a director actor.  This object represents the state
 * functionality required when a connected entity is engaging in the provider
 * protocol.
 *
 * @param myDirector  The director that is tracking the provider.
 * @param actor  The actor associated with the provider.
 * @param myOrdinalGenerator Counter for assigning ordinal values to new providers.
 */
internal class Provider(
        private val myDirector: Director,
        internal val actor: DirectorActor,
        private val gorgel: Gorgel,
        private val myOrdinalGenerator: OrdinalGenerator) : Comparable<Provider> {

    /** Provider load factor.  */
    internal var loadFactor = 0.0
        internal set(value) {
            myDirector.removeProvider(this)
            field = value.coerceAtLeast(0.0)
            myDirector.addProvider(this)
        }

    /** Names of context families served.  */
    private val myServices: MutableSet<String> = HashSet()

    /** Names of restricted context families served.  */
    private val myRestrictedServices: MutableSet<String> = HashSet()

    /** Number of users provider is willing to serve (-1 for no limit).  */
    internal var capacity = -1
        private set

    /** Number of users currently being served.  */
    private var myUserCount = 0

    /** Host+port for this provider, by protocol.  */
    private val myHostPorts: MutableMap<String, String> = HashMap()

    /** Contexts currently open, by name.  */
    private val myContexts: MutableMap<String, OpenContext> = HashMap()

    /** Context clone sets current open, by name.  */
    private val myCloneSets = HashMapMultiImpl<String, OpenContext>()

    /** Ordinal for consistent non-equality when load factors are equal.  */
    private val myOrdinal = myOrdinalGenerator.generate()

    override fun toString() = "P($myOrdinal)"

    /**
     * Compare this provider to another for sorting (comparison is by load
     * factor).
     */
    override fun compareTo(other: Provider): Int {
        val diff = loadFactor - other.loadFactor
        return when {
            diff < 0.0 -> -1
            0.0 < diff -> 1
            else -> myOrdinal.compareTo(other.myOrdinal)
        }
    }

    /**
     * Add a protocol to the list of protocols this provider will server with.
     *
     * @param protocol  Name of the protocol to add.
     * @param hostPort  Host+port for reaching this provider using 'protocol'.
     */
    fun addProtocol(protocol: String, hostPort: String) {
        myHostPorts[protocol] = hostPort
    }

    /**
     * Add a service to the list for this provider.
     *
     * @param contextFamily  The name of the context family to add.
     * @param newCapacity  The capacity of the provider, or -1 for no limit.
     */
    fun addService(contextFamily: String, newCapacity: Int, restricted: Boolean) {
        myServices.add(contextFamily)
        if (restricted) {
            myRestrictedServices.add(contextFamily)
        }
        capacity = newCapacity
    }

    /**
     * Get a read-only view of the contexts currently opened by this provider.
     *
     * @return a collection of this provider's open contexts.
     */
    fun contexts(): Collection<OpenContext> = myContexts.values

    /**
     * Clean up when the provider actor disconnects.
     */
    fun doDisconnect() {
        for (context in myContexts.values) {
            myDirector.removeContext(context)
        }
        myDirector.removeProvider(this)
    }

    /**
     * Return the "key" for comparing this provider against another for
     * purposes of duplicate elimination.  The key is the host+port string for
     * the protocol whose name string is lexically the highest.
     */
    fun dupKey(): String? {
        val candidateProtocol = myHostPorts.keys.maxOrNull()
                ?: ""
        return myHostPorts[candidateProtocol]
    }

    /**
     * Test if this provider is running at least one clone of some context.
     *
     * @param contextName  Name of the clone set of interest.
     *
     * @return true if the named clone set is on this provider somewhere.
     */
    fun hasClone(contextName: String) = !myCloneSets.getMulti(contextName).isEmpty

    /**
     * Test if a user is in some context on this provider.
     *
     * @param user  The name of the user to look for.
     *
     * @return true if the named user is on this provider somewhere.
     */
    fun hasUser(user: String): Boolean = myContexts.values.any { it.hasUser(user) }

    /**
     * Return the host+port for reaching this provider via a given protocol.
     *
     * @param protocol  The protocol sought.
     *
     * @return the host+port for to talk to this provider using 'protocol'.
     */
    fun hostPort(protocol: String) = myHostPorts[protocol]

    /**
     * Get a read-only view of the set of host+ports this provider supports.
     *
     * @return a collection of this provider's host+ports.
     */
    fun hostPorts(): Collection<String> = myHostPorts.values

    /**
     * Test if this provider is unable to accept more users.
     *
     * @return true if this provider has reached its capacity limit.
     */
    val isFull: Boolean
        get() = capacity in 0..myUserCount

    /**
     * Test if a given label matches this provider.
     *
     * This will be true if the label is this provider's label or one of its
     * host+port strings.
     *
     * @param label  The label to match against.
     */
    fun matchLabel(label: String) = actor.label == label || myHostPorts.containsValue(label)

    /**
     * Take note that this provider has closed a context.
     *
     * @param name  The context that was closed.
     */
    fun noteContextClose(name: String) {
        var context = myContexts[name]
        if (context != null) {
            myDirector.removeContext(context)
            myContexts.remove(name)
            if (context.isClone) {
                myCloneSets.remove(context.cloneSetName!!, context)
            }
        } else {
            context = myDirector.getContext(name)
            if (context != null) {
                gorgel.i?.run { info("$actor reported closure of context $name belonging to another provider (likely dup)") }
            } else {
                gorgel.i?.run { info("$actor reported closure of non-existent context $name") }
            }
        }
    }

    /**
     * Open or close a context's gate, controlling entry of new users.
     *
     * @param name  The context whose gate is being controlled.
     * @param open  True if the gate is being opened, false if closed.
     * @param reason  String indicating why the gate is being closed; ignored
     * if the gate is being opened.
     */
    fun noteContextGateSetting(name: String, open: Boolean, reason: String?) {
        val context = myContexts[name]
        if (context != null) {
            if (open) {
                context.openGate()
            } else {
                context.closeGate(reason)
            }
        } else {
            gorgel.i?.run { info("$actor set gate for non-existent context $name") }
        }
    }

    /**
     * Take note that this provider has opened a context.
     *
     * @param name  The context that was opened.
     * @param mine  true if this director is the one who asked the context to
     * open (for use in closing duplicate opens).
     * @param maxCapacity  The maximum user capacity for the context.
     * @param baseCapacity  The base capacity for the (clone) context.
     * @param restricted  true if this context is entry restricted
     */
    fun noteContextOpen(name: String, mine: Boolean, maxCapacity: Int,
                        baseCapacity: Int, restricted: Boolean) {
        var newContext: OpenContext? = OpenContext(this, name, mine, maxCapacity,
                baseCapacity, restricted)
        val oldContext = myDirector.getContext(name)
        if (oldContext != null) {
            val dupToClose = oldContext.pickDupToClose(newContext!!)
            if (dupToClose.isMine) {
                dupToClose.provider.actor.send(
                        msgClose(myDirector.providerHandler, dupToClose.name, null, true))
            }
            if (dupToClose === newContext) {
                newContext = null
            } else {
                myDirector.removeContext(oldContext)
            }
        }
        if (newContext != null) {
            myDirector.addContext(newContext)
            myContexts[name] = newContext
            if (newContext.isClone) {
                myCloneSets.add(newContext.cloneSetName!!, newContext)
            }
        }
    }

    /**
     * Take note that a user has entered one of this provider's contexts.
     *
     * @param contextName  The name of the context entered.
     * @param userName  The name of the user who entered it.
     */
    fun noteUserEntry(contextName: String, userName: String) {
        val context = myContexts[contextName]
        if (context != null) {
            context.addUser(userName)
            myDirector.addUser(userName, context)
            ++myUserCount
        } else {
            gorgel.error("$actor reported entry of $userName to non-existent context $contextName")
        }
    }

    /**
     * Take note that a user has exited one of this provider's contexts.
     *
     * @param contextName  The name of the context exited.
     * @param userName  The name of the user who exited from it.
     */
    fun noteUserExit(contextName: String, userName: String) {
        val context = myContexts[contextName]
        if (context != null) {
            myDirector.removeUser(userName, context)
            context.removeUser(userName)
            --myUserCount
        } else {
            gorgel.error("$actor reported exit of $userName from non-existent context $contextName")
        }
    }

    /**
     * Get a read-only view of the set of protocols this provider supports.
     *
     * @return a set of this provider's protocols.
     */
    fun protocols(): Set<String> = myHostPorts.keys

    /**
     * Get a read-only view of the set of services this provider supports.
     *
     * @return a set of this provider's services.
     */
    fun services(): Set<String> = myServices

    /**
     * Test if this provider will provide a particular service.
     *
     * @param service  Name of the service desired.
     * @param protocol  Protocol desired to access it by.
     * @param isInternal  Flag indicating a request from within the server farm
     *
     * @return true if this provider will serve 'service' using 'protocol'.
     */
    fun willServe(service: String, protocol: String, isInternal: Boolean) =
            if (!isInternal && myRestrictedServices.contains(service)) {
                false
            } else {
                myServices.contains(service) && myHostPorts.containsKey(protocol) && !isFull
            }

    init {
        myDirector.addProvider(this)
    }
}
