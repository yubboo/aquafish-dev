package com.aquafish.license;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * 测试用 Ed25519 证书权威（与 signer 端 CertificateAuthority 同形，独立存放以避免跨仓库耦合）。
 */
public final class TestCertificateAuthority {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private TestCertificateAuthority() {
    }

    public static KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    public static X509Certificate root(KeyPair ca, String commonName, Duration validity) throws Exception {
        X500Name name = new X500Name("CN=" + commonName);
        return build(ca.getPrivate(), name, ca.getPublic(), name, true, validity);
    }

    public static X509Certificate leaf(
        KeyPair ca, X509Certificate rootCert, PublicKey subjectPublic, String commonName, Duration validity
    ) throws Exception {
        X500Name issuer = X500Name.getInstance(rootCert.getSubjectX500Principal().getEncoded());
        X500Name subject = new X500Name("CN=" + commonName);
        return build(ca.getPrivate(), subject, subjectPublic, issuer, false, validity);
    }

    private static X509Certificate build(
        PrivateKey signer,
        X500Name subject,
        PublicKey subjectPublic,
        X500Name issuer,
        boolean ca,
        Duration validity
    ) throws Exception {
        Date now = new Date();
        Date notAfter = new Date(now.getTime() + validity.toMillis());
        BigInteger serial = new BigInteger(1, randomSerial());
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer, serial, now, notAfter, subject, subjectPublic
        );
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(
            Extension.keyUsage, true,
            new KeyUsage(ca ? (KeyUsage.keyCertSign | KeyUsage.cRLSign) : KeyUsage.digitalSignature)
        );
        ContentSigner contentSigner = new JcaContentSignerBuilder("Ed25519").setProvider("BC").build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(contentSigner));
    }

    private static byte[] randomSerial() {
        byte[] serial = new byte[16];
        new SecureRandom().nextBytes(serial);
        serial[0] &= 0x7F;
        return serial;
    }
}
