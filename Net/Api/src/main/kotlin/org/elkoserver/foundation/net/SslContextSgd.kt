@file:Suppress("MemberVisibilityCanBePrivate")

package org.elkoserver.foundation.net

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import org.ooverkommelig.D
import org.ooverkommelig.ObjectGraphConfiguration
import org.ooverkommelig.Once
import org.ooverkommelig.ProvidedBase
import org.ooverkommelig.SubGraphDefinition
import org.ooverkommelig.req
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class SslContextSgd(provided: Provided, objectGraphConfiguration: ObjectGraphConfiguration = ObjectGraphConfiguration()) : SubGraphDefinition(provided, objectGraphConfiguration) {
    interface Provided : ProvidedBase {
        fun props(): D<ElkoProperties>
        fun sslContextPropertyNamePrefix(): D<String>
        fun sslContextSgdGorgel(): D<Gorgel>
    }

    private val keyManagerAlgorithm by Once {
        req(provided.props()).getProperty("${req(provided.sslContextPropertyNamePrefix())}keymanageralgorithm", "SunX509")
    }

    private val keyStoreType by Once { req(provided.props()).getProperty("${req(provided.sslContextPropertyNamePrefix())}keystoretype", "JKS") }

    private val keyFile by Once {
        req(provided.props()).getProperty("${req(provided.sslContextPropertyNamePrefix())}keyfile")
                ?: throw IllegalStateException()
    }

    private val passwordChars by Once {
        req(provided.props()).getProperty("${req(provided.sslContextPropertyNamePrefix())}keypassword")?.toCharArray()
                ?: throw IllegalStateException()
    }

    private val keys by Once {
        KeyStore.getInstance(req(keyStoreType)).apply {
            load(FileInputStream(req(keyFile)), req(passwordChars))
        }
    }

    private val keyManagerFactory by Once {
        KeyManagerFactory.getInstance(req(keyManagerAlgorithm)).apply {
            init(req(keys), req(passwordChars))
        }
    }

    private val keyManagers by Once { req(keyManagerFactory).keyManagers }

    val sslContext by Once {
        try {
            SSLContext.getInstance("TLS").apply {
                init(req(keyManagers), null, null)
            }

            /* A wide variety of different kinds of problems can happen here, all
           of which entail some form of system misconfiguration and are
           unrecoverable.  The only useful thing to do is die with an
           informative message and let higher powers try again later after
           they've fixed it. */
        } catch (e: GeneralSecurityException) {
            req(provided.sslContextSgdGorgel()).error("problem initializing SSL", e)
            throw IllegalStateException(e)
        } catch (e: FileNotFoundException) {
            req(provided.sslContextSgdGorgel()).error("SSL key file not found", e)
            throw IllegalStateException(e)
        } catch (e: IOException) {
            req(provided.sslContextSgdGorgel()).error("problem reading SSL key file", e)
            throw IllegalStateException(e)
        }
    }
}
