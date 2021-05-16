package org.elkoserver.server.context.model

import org.elkoserver.json.JsonLiteral
import java.util.function.Consumer

interface ContextorProtocol {
    fun resolvePendingInit(obj: BasicObject)

    fun remove(`object`: BasicObject)

    fun noteContext(context: Context, open: Boolean)

    fun noteContextGate(context: Context, open: Boolean, reason: String?)

    fun noteUser(user: User, on: Boolean)

    fun notifyPendingObjectCompletionWatchers()

    val session: SessionProtocol

    fun pushNewContext(who: User, contextRef: String)

    val refTable: RefTableProtocol

    fun getStaticObject(ref: String): Any?

    fun setContents(container: BasicObject, subID: String, contents: Array<Item>?)

    fun createItem(name: String, container: BasicObject?,
                   isPossibleContainer: Boolean, isDeletable: Boolean): Item

    fun writeObjectDelete(ref: String, handler: Consumer<Any?>? = null)

    fun writeObjectState(ref: String, state: BasicObject, handler: Consumer<Any?>? = null)

    fun relay(source: BasicObject, message: JsonLiteral)

    fun loadItemContents(item: Item, handler: Consumer<Any?>)

    fun addPendingObjectCompletionWatcher(watcher: ObjectCompletionWatcher)

    fun createObjectRecord(ref: String?, contRef: String?, obj: BasicObject)
}
