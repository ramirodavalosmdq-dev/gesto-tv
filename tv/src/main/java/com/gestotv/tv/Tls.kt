package com.gestotv.tv

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory

/**
 * Certificado propio del TV, generado una sola vez y guardado en el TV.
 * Hace falta HTTPS para que Chrome en el celular permita usar la cámara.
 */
object Tls {
    private val PASS = "gestotv".toCharArray()

    fun serverSocketFactory(ctx: Context): SSLServerSocketFactory {
        val file = File(ctx.filesDir, "server.p12")
        val ks = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { ks.load(it, PASS) }
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            val now = System.currentTimeMillis()
            val name = X500Name("CN=Gesto TV")
            val builder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now),
                Date(now - 86_400_000L), Date(now + 3650L * 86_400_000L),
                name, kp.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            ks.load(null, null)
            ks.setKeyEntry("gesto", kp.private, PASS, arrayOf(cert))
            file.outputStream().use { ks.store(it, PASS) }
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, PASS)
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(kmf.keyManagers, null, null)
        return ssl.serverSocketFactory
    }
}
