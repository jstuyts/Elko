package org.elkoserver.server.presence

import org.elkoserver.foundation.json.Injector
import org.elkoserver.foundation.json.JsonMethod
import org.elkoserver.json.JsonObject
import org.elkoserver.util.trace.slf4j.Gorgel
import java.lang.reflect.InvocationTargetException

internal class GraphDesc @JsonMethod("class", "name", "?conf") constructor(
        private val myClassName: String,
        private val myGraphName: String,
        conf: JsonObject?) : DomainRegistryUsingObject, InjectorsUsingObject {
    private val myConf: JsonObject = conf ?: JsonObject()

    private lateinit var graphInjectors: Collection<Injector>

    private lateinit var myDomainRegistry: DomainRegistry

    override fun setDomainRegistry(domainRegistry: DomainRegistry) {
        myDomainRegistry = domainRegistry
    }

    override fun setInjectors(injectors: Collection<Injector>) {
        graphInjectors = injectors
    }

    fun init(master: PresenceServer, gorgel: Gorgel, socialGraphGorgel: Gorgel) =
            try {
                (Class.forName(myClassName).getConstructor().newInstance() as SocialGraph).apply {
                    graphInjectors.forEach { it.inject(this) }
                    init(master, socialGraphGorgel, Domain(myGraphName, myDomainRegistry), myConf)
                }
            } catch (e: ClassNotFoundException) {
                gorgel.error("class $myClassName not found")
                null
            } catch (e: InstantiationException) {
                gorgel.error("unable to instantiate $myClassName: $e")
                null
            } catch (e: IllegalAccessException) {
                gorgel.error("unable to instantiate $myClassName: $e")
                null
            } catch (e: NoSuchMethodException) {
                gorgel.error("class $myClassName does not have a public no-arg constructor: $e")
                null
            } catch (e: InvocationTargetException) {
                gorgel.error("error occurred during instantiation of $myClassName: ${e.cause}")
                null
            }
}
