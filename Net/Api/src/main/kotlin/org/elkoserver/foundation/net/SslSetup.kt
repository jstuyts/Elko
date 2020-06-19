package org.elkoserver.foundation.net

import org.elkoserver.foundation.properties.ElkoProperties
import org.elkoserver.util.trace.slf4j.Gorgel
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

object SslSetup {
    fun setupSsl(properties: ElkoProperties, propertyNamePrefix: String, gorgel: Gorgel): SSLContext {
        val result: SSLContext
        result = try {
            tryToSetupSsl(properties, propertyNamePrefix)

            /* A wide variety of different kinds of problems can happen here, all
           of which entail some form of system misconfiguration and are
           unrecoverable.  The only useful thing to do is die with an
           informative message and let higher powers try again later after
           they've fixed it. */
        } catch (e: GeneralSecurityException) {
            gorgel.error("problem initializing SSL", e)
            throw IllegalStateException(e)
        } catch (e: FileNotFoundException) {
            gorgel.error("SSL key file not found", e)
            throw IllegalStateException(e)
        } catch (e: IOException) {
            gorgel.error("problem reading SSL key file", e)
            throw IllegalStateException(e)
        }
        return result
    }

    @Throws(KeyStoreException::class, IOException::class, NoSuchAlgorithmException::class, CertificateException::class, UnrecoverableKeyException::class, KeyManagementException::class)
    private fun tryToSetupSsl(properties: ElkoProperties, propertyNamePrefix: String): SSLContext {
        val keyStoreType = properties.getProperty("${propertyNamePrefix}keystoretype", "JKS")
        val keys = KeyStore.getInstance(keyStoreType)
        val keyPassword = properties.getProperty("${propertyNamePrefix}keypassword") ?: throw IllegalStateException()
        val passwordChars = keyPassword.toCharArray()
        val keyFile = properties.getProperty("${propertyNamePrefix}keyfile") ?: throw IllegalStateException()
        keys.load(FileInputStream(keyFile), passwordChars)
        val keyManagerAlgorithm = properties.getProperty("${propertyNamePrefix}keymanageralgorithm", "SunX509")
        val keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm)
        keyManagerFactory.init(keys, passwordChars)
        val keyManagers = keyManagerFactory.keyManagers
        val result = SSLContext.getInstance("TLS")
        result.init(keyManagers, null, null)
        return result
    }
}
