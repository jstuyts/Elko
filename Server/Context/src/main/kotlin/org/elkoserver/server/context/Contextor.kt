package org.elkoserver.server.context

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParserException
import org.elkoserver.foundation.actor.RefTable
import org.elkoserver.foundation.json.MessageHandlerException
import org.elkoserver.foundation.net.Connection
import org.elkoserver.foundation.server.Server
import org.elkoserver.foundation.server.ServiceLink
import org.elkoserver.foundation.server.metadata.HostDesc
import org.elkoserver.foundation.timer.Timer
import org.elkoserver.json.JsonDecodingException
import org.elkoserver.json.JsonLiteral
import org.elkoserver.json.JsonParsing
import org.elkoserver.json.Referenceable
import org.elkoserver.json.getRequiredString
import org.elkoserver.objectdatabase.ObjectDatabase
import org.elkoserver.server.context.model.BasicObject
import org.elkoserver.server.context.model.Context
import org.elkoserver.server.context.model.ContextorProtocol
import org.elkoserver.server.context.model.Item
import org.elkoserver.server.context.model.Mod
import org.elkoserver.server.context.model.ObjectCompletionWatcher
import org.elkoserver.server.context.model.RefTableProtocol
import org.elkoserver.server.context.model.User
import org.elkoserver.server.context.model.extractBaseRef
import org.elkoserver.util.HashMapMultiImpl
import org.elkoserver.util.tokenize
import org.elkoserver.util.trace.slf4j.Gorgel
import org.elkoserver.util.trace.slf4j.Tag
import java.util.LinkedList
import java.util.Random
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.math.abs

/**
 * Main state data structure in a Context Server.
 *
 * This constructor exists only because of a Java limitation: it needs to
 * create the object database object and then both save it in an instance
 * variable AND pass it to the superclass constructor.  However, Java
 * requires that the first statement in a constructor MUST be a call to
 * the superclass constructor or to another constructor of the same class.
 * It is possible to create the database and pass it to the superclass
 * constructor or to save it in an instance variable, but not both.  To get
 * around this, the public constructor creates the database object in a
 * parameter expression in a call to this internal constructor, which will
 * then possess it in a parameter variable whence it can be both passed to
 * the superclass constructor and saved in an instance variable.
 *
 * @param objectDatabase  Database for persistent object storage.
 * @param server  Server object.
 * @param myRandom Random number generator, for creating unique IDs and sub-IDs.
 */
class Contextor internal constructor(
    val objectDatabase: ObjectDatabase,
    private val server: Server,
    private val runner: Executor,
    internal val realRefTable: RefTable,
    private val contextorGorgel: Gorgel,
    private val contextGorgelWithoutRef: Gorgel,
    private val itemGorgelWithoutRef: Gorgel,
    private val staticObjectReceiverGorgel: Gorgel,
    private val presencerGroupFactory: PresencerGroupFactory,
    private val directorGroupFactory: DirectorGroupFactory,
    sessionFactory: SessionFactory,
    private val timer: Timer,
    internal val entryTimeout: Int,
    internal val limit: Int,
    private val myRandom: Random,
    staticsToLoad: String?,
    private val families: String?
) : ContextorProtocol {

    override val refTable = object : RefTableProtocol {
        override fun addRef(target: Referenceable) {
            realRefTable.addRef(target)
        }

        override fun addClass(targetClass: Class<in Mod>) {
            realRefTable.addClass(targetClass)
        }

        override fun get(ref: String) = realRefTable[ref]
    }

    /** The generic 'session' object for talking to this server.  */
    override val session: Session = sessionFactory.create(this)

    /** Sets of entities awaiting objects from the object database, by object
     * reference string.  */
    private val myPendingGets: MutableMap<String?, MutableSet<Consumer<Any?>>> = HashMap()

    /** Open contexts.  */
    private val myContexts: MutableSet<Context> = HashSet()

    /** Cloned contexts, by base reference string.  */
    private val myContextClones = HashMapMultiImpl<String, Context>()

    /** Currently connected users.  */
    private val myUsers: MutableSet<User> = HashSet()

    /** Send group for currently connected directors.  */
    private var myDirectorGroup: DirectorGroup? = null

    /** Send group for currently connected presence servers.  */
    private var myPresencerGroup: PresencerGroup? = null

    /** Static objects loaded from the object database and available in all contexts.  */
    private val myStaticObjects: MutableMap<String, Any> = HashMap()

    /** Context families served by this server.  Names prefixed by '$'
     * represent restricted contexts.  */
    internal val contextFamilies: Set<String>

    /** User names gathered from presence notification metadata.  */
    private val myUserNames: MutableMap<String, String> = HashMap()

    /** Context names gathered from presence notification metadata.  */
    private val myContextNames: MutableMap<String, String> = HashMap()

    /** Mods on completed objects awaiting notification that they're ready.  */
    private var myPendingObjectCompletionWatchers: MutableList<ObjectCompletionWatcher>? = null

    private fun initializeContextFamilies() =
        HashSet<String>().apply {
            add("c")
            add("ctx")
            add("context")
            add("\$rc")
            @Suppress("SpellCheckingInspection")
            add("\$rctx")
            @Suppress("SpellCheckingInspection")
            add("\$rcontext")

            families?.tokenize(' ', ',', ';', ':')?.forEach { tag ->
                add(tag)
            }
        }

    private fun isValidContextRef(ref: String): Boolean {
        val delim = ref.indexOf('-')
        return if (delim < 0) {
            false
        } else {
            val family = ref.take(delim)
            contextFamilies.contains(family) || contextFamilies.contains("$$family")
        }
    }

    /**
     * Add to the list of Mods awaiting notification that their objects are
     * done.
     *
     * @param watcher  The watching Mod to be notified.
     */
    override fun addPendingObjectCompletionWatcher(watcher: ObjectCompletionWatcher) {
        val currentPendingObjectCompletionWatchers = myPendingObjectCompletionWatchers
        val actualPendingObjectCompletionWatchers = if (currentPendingObjectCompletionWatchers == null) {
            val newPendingObjectCompletionWatchers = LinkedList<ObjectCompletionWatcher>()
            myPendingObjectCompletionWatchers = newPendingObjectCompletionWatchers
            newPendingObjectCompletionWatchers
        } else {
            currentPendingObjectCompletionWatchers
        }
        actualPendingObjectCompletionWatchers.add(watcher)
    }

    /**
     * Notify any Mods still awaiting notification that their objects are done.
     *
     * As a side effect, this will clear the list of who is waiting.
     */
    override fun notifyPendingObjectCompletionWatchers() {
        val currentPendingObjectCompletionWatchers = myPendingObjectCompletionWatchers
        if (currentPendingObjectCompletionWatchers != null) {
            val targets: List<ObjectCompletionWatcher> = currentPendingObjectCompletionWatchers
            myPendingObjectCompletionWatchers = null
            for (target in targets) {
                target.objectIsComplete()
            }
        }
    }

    /**
     * Activate an item that is contained by another object as part of its
     * being loaded.
     *
     * @param container  The container into which the item is placed.
     * @param subID  Sub-ID string for cloned objects.  This should be an empty
     * string if clones are not being generated.
     * @param item  Inactive item that is being activated.
     */
    private fun activateContentsItem(container: BasicObject, subID: String, item: Item) {
        val ref = item.ref() + subID
        activateItem(item, ref, subID, container.isEphemeral)
        item.setContainerPrim(container)
        item.objectIsComplete()
    }

    /**
     * Take note that somebody is waiting for an object from the object
     * database.
     *
     * @param ref  Reference string for the object being fetched.
     * @param handler  Handler to be invoked on result object.
     *
     * @return true if this is the first pending get for the requested object.
     */
    private fun addPendingGet(ref: String?, handler: Consumer<Any?>): Boolean {
        var handlerSet = myPendingGets[ref]
        var isFirst = false
        if (handlerSet == null) {
            handlerSet = HashSet()
            myPendingGets[ref] = handlerSet
            isFirst = true
        }
        handlerSet.add(handler)
        return isFirst
    }

    /**
     * Add an object to the static object table.
     *
     * @param key  Name by which this object will be known within the server
     * @param obj  The object itself
     */
    fun addStaticObject(key: String, obj: Any) {
        myStaticObjects[key] = obj
        if (obj is InternalObject) {
            obj.activate(key, this)
            (obj as? AdminObject)?.let(realRefTable::addRef)
        }
    }

    /**
     * Save all changed objects that need saving.
     */
    private fun checkpointAll() {
        realRefTable
            .filterIsInstance<BasicObject>()
            .forEach(BasicObject::checkpointWithoutContents)
    }

    /**
     * Get a read-only view of the context set.
     *
     * @return the current set of open contexts.
     */
    fun contexts(): Set<Context> = myContexts

    /**
     * Common initialization logic for createItem.
     */
    private fun initializeItem(item: Item, container: BasicObject?) {
        val ref = uniqueID("i")
        activateItem(item, ref, "", false)
        item.markAsChanged()
        item.setContainer(container)
    }

    /**
     * Return a newly minted Item (i.e., one created at runtime rather than
     * loaded from the object database).  The new item will have no contents,
     * no mods, and no position.  If it is a container, it will be open.
     *
     * @param name  The name for the new item, or null if the name doesn't
     * matter.
     * @param container  The object that is to be the new item's container.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     * be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     * users.
     */
    override fun createItem(
        name: String, container: BasicObject?,
        isPossibleContainer: Boolean, isDeletable: Boolean
    ): Item {
        val item = Item(name, isPossibleContainer, isDeletable, false)
        initializeItem(item, container)
        return item
    }

    /**
     * Return a newly minted Item (i.e., one created at runtime rather than
     * loaded from the object database).  The new item will be born with no
     * contents, no mods, and no container.
     *
     * @param name  The name for the new item, or null if the name doesn't
     * matter.
     * @param isPossibleContainer  Flag that is true if the new item may itself
     * be used as a container.
     * @param isDeletable  Flag that is true if the new item may be deleted by
     * users.
     */
    @Suppress("unused")
    fun createItem(name: String, isPossibleContainer: Boolean, isDeletable: Boolean): Item =
        createItem(name, null, isPossibleContainer, isDeletable)

    /**
     * Create a new (offline) object and store its description in the object
     * database.
     *
     * @param ref  Reference string for the new object, or null to have one
     * generated automatically.
     * @param contRef  Reference string for the new object's container, or null
     * to not have it put into a container.
     * @param obj  The new object.
     */
    override fun createObjectRecord(ref: String?, contRef: String?, obj: BasicObject) {
        val actualRef = ref ?: uniqueID(obj.type())
        objectDatabase.putObject(actualRef, obj, null)
    }

    /**
     * Delete a user record from the object database.
     *
     * @param ref  Reference string identifying the user to be deleted.
     */
    @Suppress("unused")
    fun deleteUserRecord(ref: String) {
        objectDatabase.removeObject(ref, null)
    }

    /**
     * Deliver a relayed message to an instance of an object.
     *
     * @param destination  Object instance to deliver to.
     * @param message  The message to deliver.
     */
    fun deliverMessage(destination: BasicObject, message: JsonObject) {
        try {
            realRefTable.dispatchMessage(null, destination, message)
        } catch (e: MessageHandlerException) {
            contextorGorgel.i?.run { info("ignoring error from internal msg relay: $e") }
        }
    }

    /**
     * Locate an available clone of some context.
     *
     * @param ref  Base reference of the context sought.
     *
     * @return a reference to a clone of the context named by 'ref' that has
     * room for a new user, or null if 'ref' does not refer to a cloneable
     * context or if all the clones are full.
     */
    private fun findContextClone(ref: String): Context? =
        myContextClones.getMulti(ref).firstOrNull { it.userCount < it.baseCapacity && !it.gateIsClosed() }

    /**
     * Find or make a connection to an external service.
     *
     * @param serviceName  Name of the service being sought.
     * @param handler  A runnable that will be invoked with the relevant
     * service link once the connection is located or created.  The handler
     * will be passed a null if no connection was possible.
     */
    fun findServiceLink(serviceName: String, handler: Consumer<in ServiceLink?>) {
        server.findServiceLink("workshop-service-$serviceName", handler)
    }

    /**
     * Obtain a context, either by obtaining a pointer to an already loaded
     * context or, if needed, by loading it.
     *
     * @param contextRef  Reference string identifying the context sought.
     * @param contextTemplate  Optional reference the template context from
     * which the context should be derived.
     * @param contextHandler  Handler to invoke with resulting context.
     * @param opener  Director that requested this context be opened, or null
     * if not relevant.
     */
    fun getOrLoadContext(
        contextRef: String, contextTemplate: String?,
        contextHandler: Consumer<Any?>, opener: DirectorActor?
    ) {
        var actualContextTemplate = contextTemplate
        if (isValidContextRef(contextRef)) {
            var result = findContextClone(contextRef)
            if (result == null) {
                result = realRefTable[contextRef] as Context?
            }
            if (result == null) {
                if (actualContextTemplate == null) {
                    actualContextTemplate = contextRef
                }
                val getHandler: Consumer<in BasicObject?> = GetContextHandler(actualContextTemplate, contextRef, opener)
                val contentsHandler = ContentsHandler(null, getHandler)
                val contextReceiver = Consumer { obj: Any? -> contentsHandler.receiveContainer(obj as BasicObject?) }
                if (addPendingGet(actualContextTemplate, contextHandler)) {
                    objectDatabase.getObject(actualContextTemplate, contextReceiver)
                    loadContentsOfContainer(contextRef, contentsHandler)
                }
            } else {
                contextHandler.accept(result)
            }
        } else {
            contextHandler.accept(null)
        }
    }

    /**
     * Thunk class to receive the contents of a container object.  When a
     * top-level container (i.e., a context or a user) is loaded, we need to
     * also load the container's contents, and the contents of the contents,
     * and so on.  We don't want to signal the top-level container as being
     * successfully loaded until all the things that are descended from it are
     * also loaded.  This class manages that process.  Each instance of this
     * class represents a container that is being loaded; it tracks the loading
     * of its contents and then notifies the container that contains *it*.
     *
     * @param myParentHandler ContentsHandler for the enclosing parent
     * container, or null if we are the top level container.
     * @param myTopHandler Thunk to be notified with the complete result once
     * it is available.
     */
    private inner class ContentsHandler(
        private val myParentHandler: ContentsHandler?,
        private val myTopHandler: Consumer<in BasicObject?>
    ) : Consumer<Any?> {

        /** Number of objects whose loading is being awaited.  Initially, this
         * is the number of contained objects plus two: the container itself,
         * the array of contents objects, and the contents of each of those
         * contents objects. It counts down as loading completes.  */
        private var myWaitCount = 2

        /** The container object this handler is handling the loading of
         * contents for.  Initially, this is null; it acquires a value when
         * the external entity that actually fetches the container object
         * calls the receiveContainer() method.  */
        private var myContainer: BasicObject? = null

        /** Flag indicating that 'myContainer' has been set.  */
        private var haveContainer = false

        /** Array of contents objects whose contents this handler is overseeing
         * the recursive loading of.  Initially, this is null; it acquires a
         * value when the external entity that actually fetches the contents
         * objects calls the receiveContents() method.  */
        private var myContents: Array<Item>? = null

        /** Flag indicating that 'myContents' has been set.  */
        private var haveContents = false

        /**
         * Indicate that an additional quantity of objects await being loaded.
         *
         * @param count  The number of additional objects to wait for.
         */
        private fun expectMore(count: Int) {
            myWaitCount += count
        }

        /**
         * Indicate that some number of objects have been successfully loaded.
         *
         * @param count The number of objects that have been loaded (typically
         * this will be 1) or -1 to indicate that all objects that are ever
         * going to be loaded have been (typically, because an error of some
         * kind has terminated loading).
         */
        private fun somethingArrived(count: Int) {
            if (0 <= myWaitCount) {
                if (count < 0) {
                    myWaitCount = 0
                } else {
                    myWaitCount -= count
                }
                if (myWaitCount == 0) {
                    if (haveContents && haveContainer) {
                        val currentContents = myContents
                        if (currentContents != null) {
                            myContainer?.addPassiveContents(currentContents)
                        }
                    }
                    myWaitCount = -1
                    if (myParentHandler == null) {
                        myTopHandler.accept(myContainer)
                    } else {
                        myParentHandler.somethingArrived(1)
                    }
                }
            }
        }

        /**
         * Note the arrival of the container object itself.
         *
         * @param container  The container object.
         */
        fun receiveContainer(container: BasicObject?) {
            myContainer = container
            haveContainer = true
            somethingArrived(1)
        }

        /**
         * Note the arrival of the contents objects themselves.
         *
         * @param contents Array of contents objects (but not *their*
         * contents).
         */
        fun receiveContents(contents: Array<Item>?) {
            myContents = contents
            haveContents = true
            somethingArrived(1)
        }

        /**
         * Runnable invoked by the object database to accept the delivery of
         * stuff fetched from the database.
         *
         * @param obj The thing that was obtained from the database.  In the
         * current case, this will *always* be an array of objects
         * representing the contents of the container object this handler is
         * handling.
         */
        override fun accept(obj: Any?) {
            val contents: Array<Item>
            if (obj != null) {
                @Suppress("UNCHECKED_CAST") val rawContents = obj as Array<Any>
                if (rawContents.isEmpty()) {
                    somethingArrived(-1)
                } else {
                    expectMore(rawContents.size)
                    @Suppress("UNCHECKED_CAST")
                    contents = arrayOf(*(rawContents as Array<Item>))
                    contents.forEach { item ->
                        if (item.isContainer && !item.isClosed) {
                            val subHandler = ContentsHandler(this, myTopHandler)
                            subHandler.receiveContainer(item)
                            loadContentsOfContainer(item.ref(), subHandler)
                        } else {
                            somethingArrived(1)
                        }
                    }
                    receiveContents(contents)
                }
            } else {
                somethingArrived(-1)
            }
        }

    }

    /**
     * Fetch the (direct) contents of a container from the repository.
     *
     * @param containerRef  Ref of the container object.
     * @param handler  Runnable to be invoked with the retrieved objects.
     */
    private fun loadContentsOfContainer(
        containerRef: String,
        handler: Consumer<Any?>
    ) {
        queryObjects(contentsQuery(extractBaseRef(containerRef)), 0, handler)
    }

    /**
     * Thunk class to receive a context object fetched from the database.  At
     * the point this is invoked, the context and all of its contents are
     * loaded but not activated.
     *
     * @param myContextTemplate  The ref of the context template.
     * @param myContextRef  The ref of the context itself.
     * @param myOpener  The director who requested the context activation.
     */
    private inner class GetContextHandler(
        private val myContextTemplate: String,
        private var myContextRef: String,
        private val myOpener: DirectorActor?
    ) : Consumer<BasicObject?> {

        /**
         * Callback that will be invoked when the context is loaded.
         *
         * @param obj  The object that was fetched.  This will be a Context
         * object with a fully expanded (but unactivated) contents tree.
         */
        override fun accept(obj: BasicObject?) {
            var context: Context? = null
            if (obj is Context) {
                context = obj
            }
            if (context != null) {
                val spawningTemplate = myContextRef != myContextTemplate
                val spawningClone = 0 < context.baseCapacity
                if (!spawningTemplate && context.isMandatoryTemplate) {
                    contextorGorgel.error("context '$myContextRef' may only be used as a template")
                    context = null
                } else if (!spawningTemplate ||
                    context.isAllowableTemplate
                ) {
                    var subID = ""
                    if (spawningClone || spawningTemplate) {
                        subID = uniqueID("")
                    }
                    if (spawningClone) {
                        myContextRef += subID
                    }
                    context.activate(
                        myContextRef, subID,
                        myContextRef != myContextTemplate,
                        this@Contextor, runner, myContextTemplate,
                        myOpener, contextGorgelWithoutRef.withAdditionalStaticTags(Tag("ref", myContextRef)), timer
                    )
                    context.objectIsComplete()
                    notifyPendingObjectCompletionWatchers()
                } else {
                    contextorGorgel.error("context '$myContextTemplate' may not be used as a template")
                    context = null
                }
            }
            if (context == null) {
                contextorGorgel.error("unable to load context '$myContextTemplate' as '$myContextRef'")
                resolvePendingGet(myContextTemplate, null)
            } else if (context.isReady) {
                resolvePendingGet(myContextTemplate, context)
            }
        }
    }

    override fun resolvePendingInit(obj: BasicObject) {
        if (obj.isReady) {
            resolvePendingGet(obj.baseRef(), obj)
        }
    }

    /**
     * Obtain an item, either by obtaining a pointer to an already loaded item
     * or, if needed, by loading it.
     *
     * @param itemRef  Reference string identifying the item sought.
     * @param itemHandler  Handler to invoke with the resulting item.
     */
    @Suppress("unused")
    fun getOrLoadItem(itemRef: String, itemHandler: Consumer<Any?>) {
        if (itemRef.startsWith("item-") || itemRef.startsWith("i-")) {
            val result = realRefTable[itemRef] as Item?
            if (result == null) {
                if (addPendingGet(itemRef, itemHandler)) {
                    objectDatabase.getObject(itemRef, GetItemHandler(itemRef))
                }
            } else {
                itemHandler.accept(result)
            }
        } else {
            itemHandler.accept(null)
        }
    }

    private inner class GetItemHandler(private val myItemRef: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            if (obj != null) {
                val item = obj as Item
                activateItem(item, myItemRef, "", false)
                item.objectIsComplete()
                if (item.isReady) {
                    resolvePendingGet(myItemRef, item)
                }
            }
        }

    }

    private fun activateItem(item: Item, ref: String, subID: String, isEphemeral: Boolean) {
        item.activate(ref, subID, isEphemeral, this, itemGorgelWithoutRef.withAdditionalStaticTags(Tag("ref", ref)))
    }

    /**
     * Lookup an object in the static object table.
     *
     * @param ref  Reference string denoting the object of interest.
     *
     * @return the object named 'ref' from the static object table, or null if
     * there is no such object.
     */
    override fun getStaticObject(ref: String): Any? = myStaticObjects[ref]

    /**
     * Load the contents of a previously closed container.
     *
     * @param item  The item whose contents are to be loaded.
     * @param handler  Handler to be notified once the contents are loaded.
     */
    override fun loadItemContents(item: Item, handler: Consumer<Any?>) {
        val contentsHandler = ContentsHandler(null, handler)
        contentsHandler.receiveContainer(item)
        loadContentsOfContainer(item.ref(), contentsHandler)
    }

    /**
     * Load the static objects indicated by one or more static object list
     * objects.
     *
     * @param staticListRefs  A comma separated list of static object list
     * object names.
     */
    private fun loadStaticObjects(staticListRefs: String?) {
        objectDatabase.getObject("statics", StaticObjectListReceiver("statics"))
        staticListRefs?.tokenize(' ', ',', ';', ':')?.forEach { tag ->
            objectDatabase.getObject(tag, StaticObjectListReceiver(tag))
        }
    }

    private inner class StaticObjectListReceiver(var myTag: String) : Consumer<Any?> {
        override fun accept(obj: Any?) {
            val statics = obj as StaticObjectList?
            if (statics != null) {
                contextorGorgel.i?.run { info("loading static object list '$myTag'") }
                statics.fetchFromObjectDatabase(objectDatabase, this@Contextor, staticObjectReceiverGorgel)
            } else {
                contextorGorgel.i?.run { info("unable to load static object list '$myTag'") }
            }
        }

    }

    /**
     * Lookup a User object in the object database.
     *
     * @param userRef  Reference string identifying the user sought.
     * @param scope  Application scope for filtering mods
     * @param userHandler  Handler to invoke with the resulting user object or
     * with null if the user object could not be obtained.
     */
    fun loadUser(
        userRef: String, scope: String?,
        userHandler: Consumer<Any?>
    ) {
        var actualUserHandler = userHandler
        if (userRef.startsWith("user-") || userRef.startsWith("u-")) {
            if (scope != null) {
                actualUserHandler = ScopedModAttacher(scope, actualUserHandler)
            }
            val getHandler = Consumer { obj: Any? -> resolvePendingGet(userRef, obj) }
            val contentsHandler = ContentsHandler(null, getHandler)
            val userReceiver = Consumer { obj: Any? -> contentsHandler.receiveContainer(obj as BasicObject?) }
            if (addPendingGet(userRef, actualUserHandler)) {
                objectDatabase.getObject(userRef, userReceiver)
                loadContentsOfContainer(userRef, contentsHandler)
            }
        } else {
            actualUserHandler.accept(null)
        }
    }

    /**
     * Thunk to intercept the return of a basic object from the database,
     * generate a query to fetch that object's application-scoped mods, and
     * attach those mods to the object before passing the object to whoever
     * actually asked for it originally.
     *
     * @param myScope  The application scope to be queried against.
     * @param myOuterHandler  The original handler to which the modified
     * object should be passed.
     */
    private inner class ScopedModAttacher(
        private val myScope: String,
        private val myOuterHandler: Consumer<in BasicObject?>
    ) : Consumer<Any?> {
        private var myObj: BasicObject? = null

        /**
         * Callback that will receive the scoped mod query results.
         *
         * In normal operation, this will end up getting invoked twice.
         *
         * The first time this is called, the arg will be a BasicObject.  This
         * will be the object that was originally requested.  If it is null,
         * then the original query failed and the null will be passed to the
         * outer handler and our work here is done (albeit in a failure state).
         * Otherwise, the ref of the object combined with the scope passed to
         * our constructor are used to form a query to fetch the object's
         * scoped mods.
         *
         * The second time this is called, the arg will be an array of Mod
         * objects (though it may be null or an empty array, which means that
         * the set of mods is empty).  These mods (if there are any) are
         * attached to the object from the first callback and then the object
         * is passed to the outer handler.
         *
         * @param arg  The object or objects fetched from the database, per
         * the above description.
         */
        override fun accept(arg: Any?) {
            val currentObj = myObj
            if (currentObj == null) {
                if (arg == null) {
                    myOuterHandler.accept(null)
                } else {
                    val argAsBasicObject = arg as BasicObject
                    myObj = argAsBasicObject
                    queryObjects(scopeQuery(argAsBasicObject.ref(), myScope), 0, this)
                }
            } else {
                if (arg != null) {
                    @Suppress("UNCHECKED_CAST") val rawMods = arg as Array<Any>
                    for (rawMod in rawMods) {
                        currentObj.attachMod(rawMod as Mod)
                    }
                }
                myOuterHandler.accept(currentObj)
            }
        }

    }

    /**
     * Lookup a reservation.
     *
     * @param who  Whose reservation?
     * @param where  For where?
     * @param authCode  The alleged authCode.
     *
     * @return the requested reservation if there is one, or null if not.
     */
    fun lookupReservation(who: String?, where: String, authCode: String): Reservation? =
        myDirectorGroup!!.lookupReservation(who, where, authCode)

    /**
     * Do record keeping associated with tracking the set of open contexts:
     * tell the directors that a context has been opened or closed and update
     * the context clone collection.
     *
     * @param context  The context.
     * @param open  true if opened, false if closed.
     */
    override fun noteContext(context: Context, open: Boolean) {
        if (open) {
            myContexts.add(context)
            if (0 < context.baseCapacity) {
                myContextClones.add(context.baseRef(), context)
            }
        } else {
            myContexts.remove(context)
            if (0 < context.baseCapacity) {
                myContextClones.remove(context.baseRef(), context)
            }
        }
        myDirectorGroup?.noteContext(context, open)
        myPresencerGroup?.noteContext(context, open)
    }

    /**
     * Tell the directors that a context gate has been opened or closed.
     *
     * @param context  The context whose gate is being opened or closed
     * @param open  Flag indicating open or closed
     * @param reason  Reason for closing the gate
     */
    override fun noteContextGate(context: Context, open: Boolean, reason: String?) {
        myDirectorGroup?.noteContextGate(context, open, reason)
    }

    /**
     * Tell the directors that a user has come or gone.
     *
     * @param user  The user.
     * @param on  true if now online, false if now offline.
     */
    override fun noteUser(user: User, on: Boolean) {
        if (on) {
            myUsers.add(user)
        } else {
            myUsers.remove(user)
        }
        myDirectorGroup?.noteUser(user, on)
        myPresencerGroup?.noteUser(user, on)
    }

    /**
     * Take notice for someone that a user elsewhere has come or gone.
     *
     * @param contextRef  Ref of context of user who cares
     * @param observerRef  Ref of user who cares
     * @param domain  Presence domain of relationship between observer & who
     * @param whoRef  Ref of user who came or went
     * @param whoMeta  Optional metadata about user who came or went
     * @param whereRef  Ref of the context entered or exited
     * @param whereMeta  Optional metadata about the context entered or exited
     * @param on  True if they came, false if they left
     */
    fun observePresenceChange(
        contextRef: String, observerRef: String,
        domain: String?, whoRef: String,
        whoMeta: JsonObject?, whereRef: String,
        whereMeta: JsonObject?, on: Boolean
    ) {
        if (whoMeta != null) {
            try {
                val name = whoMeta.getRequiredString("name")
                myUserNames[whoRef] = name
            } catch (e: JsonDecodingException) {
                // No action needed. Do not add a user name.
            }
        }
        if (whereMeta != null) {
            try {
                val name = whereMeta.getRequiredString("name")
                myContextNames[whereRef] = name
            } catch (e: JsonDecodingException) {
                // No action needed. Do not add a context name.
            }
        }
        val subscriber = realRefTable[contextRef] as Context?
        if (subscriber != null) {
            subscriber.observePresenceChange(
                observerRef, domain, whoRef,
                whereRef, on
            )
        } else {
            contextorGorgel.warn("presence change of $whoRef${if (on) " entering " else " exiting "}$whereRef for $observerRef directed to unknown context $contextRef")
        }
    }

    /**
     * Obtain the name metadata for a context, as most recently reported by the
     * presence server.
     *
     * @param contextRef  The context for which the name metadata is sought.
     *
     * @return the name for the given context, or null if none has ever been
     * reported.
     */
    @Suppress("unused")
    fun getMetadataContextName(contextRef: String): String? = myContextNames[contextRef]

    /**
     * Obtain the name metadata for a user, as most recently reported by the
     * presence server.
     *
     * @param userRef  The user for whom metadata is sought.
     *
     * @return the name for the given user, or null if none has ever been
     * reported.
     */
    @Suppress("unused")
    fun getMetadataUserName(userRef: String): String? = myUserNames[userRef]

    /**
     * Push a user to a different context: obtain a reservation for the new
     * context, send it to the user, and then kick them out.  If we're not
     * using a director, just send them directly without a reservation.
     *
     * @param who  The user being pushed
     * @param contextRef  The ref of the context to push them to.
     */
    override fun pushNewContext(who: User, contextRef: String) {
        val currentDirectorGroup = myDirectorGroup
        if (currentDirectorGroup != null) {
            currentDirectorGroup.pushNewContext(who, contextRef)
        } else {
            who.exitWithContextChange(contextRef, null, null)
        }
    }

    /**
     * Query the attached object store.
     *
     * @param template  Query template indicating the object(s) desired.
     * @param maxResults  Maximum number of result objects to return, or 0 to
     * indicate no fixed limit.
     * @param handler  Handler to be called with the results.  The results will
     * be an array of the object(s) requested, or null if no objects could
     * be retrieved.
     *
     * XXX Is this a POLA (Principle of Least Authority) violation??
     */
    fun queryObjects(template: JsonObject, maxResults: Int, handler: Consumer<Any?>) {
        objectDatabase.queryObjects(template, maxResults, handler)
    }

    /**
     * Register this server's list of listeners with its list of directors.
     *
     * @param directors  List of HostDesc objects describing directors with
     * whom to register.
     * @param listeners  List of HostDesc objects describing active
     * listeners to register with the indicated directors.
     */
    fun registerWithDirectors(directors: List<HostDesc>, listeners: List<HostDesc>) {
        val group = directorGroupFactory.create(this, directors, listeners)
        if (group.isLive) {
            myDirectorGroup = group
        }
    }

    /**
     * Register this server with its list of presence servers.
     *
     * @param presencers  List of HostDesc objects describing presence servers
     * with whom to register.
     */
    fun registerWithPresencers(presencers: List<HostDesc>) {
        val group = presencerGroupFactory.create(this, presencers)
        if (group.isLive) {
            myPresencerGroup = group
        }
    }

    /**
     * Reinitialize the server.
     */
    fun reinitServer() {
        server.reinit()
    }

    /**
     * Relay a message from an object to its clones.
     *
     * @param source  Object that is sending the message.
     * @param message  The message itself.
     */
    override fun relay(source: BasicObject, message: JsonLiteral) {
        if (source.isClone) {
            val baseRef = source.baseRef()
            var contextRef: String? = null
            var userRef: String? = null
            when (source) {
                is Context -> contextRef = baseRef
                is User -> userRef = baseRef
                else -> throw Error("relay from inappropriate object")
            }
            var msgObject: JsonObject? = null
            for (target in realRefTable.clones(baseRef)) {
                val obj = target as BasicObject
                if (obj !== source) {
                    /* Generating the text form of the message and then
                       parsing it internally may seem like a ludicrously
                       inefficient way to do this, but it saves a vast
                       amount of complication that would otherwise result
                       if internal message relay had to be treated as a
                       special case.  Note that the expensive operations
                       are conditional inside the loop, so that if there is
                       no local relaying to do, no parsing is done, and it
                       is only ever done once in any case.  If this
                       actually turns out to be a performance issue in
                       practice (unlikely, IMHO), this can be revisited. */
                    msgObject = msgObject ?: try {
                        JsonParsing.jsonObjectFromString(message.sendableString()) ?: throw IllegalStateException()
                    } catch (e: JsonParserException) {
                        contextorGorgel.error("syntax error in internal JSON message: ${e.message}")
                        break
                    }
                    deliverMessage(obj, msgObject)
                }
            }
            myDirectorGroup?.relay(baseRef, contextRef, userRef, message)
        }
    }

    /**
     * Remove an object and all of its contents (recursively) from the table.
     *
     * @param object  The object to remove.
     */
    override fun remove(`object`: BasicObject) {
        for (item in `object`.contents()) {
            remove(item)
        }
        realRefTable.remove(`object`)
    }

    /**
     * Inform everybody who has been waiting for an object from the object
     * database that the object is here.
     *
     * @param ref  The reference string for the object that arrived.
     * @param obj  The object itself, or null if it could not be obtained.
     */
    private fun resolvePendingGet(ref: String, obj: Any?) {
        val actualRef = extractBaseRef(ref)
        val handlerSet: Set<Consumer<Any?>>? = myPendingGets[actualRef]
        if (handlerSet != null) {
            myPendingGets.remove(actualRef)
            for (handler in handlerSet) {
                handler.accept(obj)
            }
        }
    }

    /**
     * Get the server name.
     *
     * @return the server's name.
     */
    fun serverName(): String = server.serverName

    /**
     * Convert an array of Items into the contents of a container.  This
     * routine does not fiddle with the changed flags and is for use during
     * object construction only.
     *
     * @param container  The container into which the items are being placed.
     * @param subID  Sub-ID string for cloned objects.  This should be an empty
     * string if clones are not being generated.
     * @param contents  Array of inactive items to be added to the container.
     */
    override fun setContents(container: BasicObject, subID: String, contents: Array<Item>?) {
        contents?.forEach { item ->
            activateContentsItem(container, subID, item)
        }
    }

    fun shutDown() {
        /* List copy to avert ConcurrentModificationException */
        val saveUsers: List<User> = LinkedList(myUsers)
        for (user in saveUsers) {
            user.exitContext(
                "server shutting down", "shutdown",
                false
            )
        }
        myDirectorGroup?.disconnectHosts()
        myPresencerGroup?.disconnectHosts()
        checkpointAll()
    }

    /**
     * Synthesize a User object by having a factory object (from the static
     * object table) produce it.
     *
     * @param connection  The connection over which the new user presented
     * themselves.
     * @param factoryTag  Tag identifying the factory to use
     * @param param  Arbitrary parameter object, which should be consistent
     * with the factory indicated by 'factoryTag'
     * @param contextRef  Ref of context the new synthesized user will be
     * placed into
     * @param contextTemplate  Ref of the context template for the context
     * @param userHandler  Handler to invoke with the resulting user object or
     * with null if the user object could not be produced.
     */
    fun synthesizeUser(
        connection: Connection, factoryTag: String,
        param: JsonObject?, contextRef: String,
        contextTemplate: String?,
        userHandler: Consumer<in User?>
    ) {
        when (val rawFactory = getStaticObject(factoryTag)) {
            null -> {
                contextorGorgel.error("user factory '$factoryTag' not found")
                userHandler.accept(null)
            }
            is EphemeralUserFactory -> {
                val user = rawFactory.provideUser(this, connection, param, contextRef, contextTemplate)
                user.markAsEphemeral()
                userHandler.accept(user)
            }
            is UserFactory -> rawFactory.provideUser(this, connection, param, userHandler)
            else -> {
                contextorGorgel.error("factory tag '$factoryTag' does not designate a user factory object")
                userHandler.accept(null)
            }
        }
    }

    /**
     * Generate a unique object ID.
     *
     * @param prefix The prefix for the new ID.
     *
     * @return a reference string for a new object with the given root.
     */
    fun uniqueID(prefix: String): String = "$prefix-${abs(myRandom.nextLong())}"

    /**
     * Get the current number of users.
     *
     * @return the number of users currently in all contexts.
     */
    fun userCount(): Int = myUsers.size

    /**
     * Get a read-only view of the user set.
     *
     * @return the current set of open users.
     */
    fun users(): Set<User> = myUsers

    /**
     * Record an object deletion in the object database, with completion
     * handler.
     *
     * @param ref  Reference string designating the deleted object.
     * @param handler  Completion handler.
     */
    override fun writeObjectDelete(ref: String, handler: Consumer<Any?>?) {
        objectDatabase.removeObject(ref, handler)
    }

    /**
     * Write an object's state to the object database, with completion handler.
     *
     * @param ref  Reference string of the object to write.
     * @param state  The object state to be written.
     * @param handler  Completion handler
     */
    override fun writeObjectState(ref: String, state: BasicObject, handler: Consumer<Any?>?) {
        objectDatabase.putObject(ref, state, handler)
    }

    init {
        realRefTable.addRef(session)
        server.setServiceRefTable(realRefTable)
        contextFamilies = initializeContextFamilies()
        loadStaticObjects(staticsToLoad)
    }
}
