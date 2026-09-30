// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.testing;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;

import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Builds throw-away EC certificate authorities and leaf certificates for tests.
 *
 * <p>Certificates are generated fresh on every run rather than checked in as fixtures, because the
 * chain verification they feed enforces validity windows against wall-clock time and offers no way
 * to override the validation date. A hard-coded certificate would silently start failing the day it
 * expired.
 *
 * <p>This lives in {@code heidi-shared-backend} and ships as a test-jar so both the platform API and
 * the verifier can build the same material; neither reactor has to depend on the other's tests.
 */
public final class TestCertificates {

    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    static {
        Security.addProvider(BC);
    }

    private TestCertificates() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    /** A generated certificate authority: its key pair and its self-signed certificate. */
    public record Ca(String commonName, KeyPair keyPair, X509Certificate certificate) {

        /** The CA's key material as a JWK, for tests that sign with it directly. */
        public ECKey jwk() {
            return new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic())
                    .privateKey((ECPrivateKey) keyPair.getPrivate())
                    .build();
        }
    }

    /** A leaf certificate together with the base64 leaf-to-root chain it belongs to. */
    public record Leaf(X509Certificate certificate, List<String> encodedChain) {}

    /** A PKCS#12 keystore holding a generated key and its leaf-to-root chain. */
    public record Pkcs12(byte[] keystore, String password, List<String> encodedChain) {

        public String base64() {
            return Base64.getEncoder().encodeToString(keystore);
        }
    }

    public static KeyPair ecKeyPair() throws Exception {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** A public-key-only JWK, standing in for a verifier's key. */
    public static ECKey publicJwk(final KeyPair keyPair) {
        return new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic()).build();
    }

    /** A CA valid from yesterday for a year. */
    public static Ca ca(final String commonName) throws Exception {
        final var now = Instant.now();
        return ca(commonName, now.minus(1, ChronoUnit.DAYS), now.plus(365, ChronoUnit.DAYS));
    }

    public static Ca ca(final String commonName, final Instant notBefore, final Instant notAfter)
            throws Exception {
        final var keyPair = ecKeyPair();
        final var name = new X500Name("CN=" + commonName);
        final var builder =
                new JcaX509v3CertificateBuilder(
                        name,
                        new BigInteger(Long.toString(notBefore.toEpochMilli())),
                        Date.from(notBefore),
                        Date.from(notAfter),
                        name,
                        keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        final var signer =
                new JcaContentSignerBuilder("SHA256WITHECDSA").build(keyPair.getPrivate());
        final var certificate =
                new JcaX509CertificateConverter()
                        .setProvider(BC)
                        .getCertificate(builder.build(signer));
        return new Ca(commonName, keyPair, certificate);
    }

    /**
     * Signs {@code subjectPublicKey} with {@code ca}, optionally carrying DNS subject alternative
     * names, and returns the leaf together with the encoded leaf-to-root chain.
     */
    public static Leaf leaf(final Ca ca, final PublicKey subjectPublicKey, final String commonName)
            throws Exception {
        return leaf(ca, subjectPublicKey, commonName, List.of());
    }

    public static Leaf leaf(
            final Ca ca,
            final PublicKey subjectPublicKey,
            final String commonName,
            final List<String> sans)
            throws Exception {
        final var now = Instant.now();
        final var notBefore = now.minus(1, ChronoUnit.DAYS);
        final var notAfter = now.plus(365, ChronoUnit.DAYS);
        final var builder =
                new JcaX509v3CertificateBuilder(
                        new X500Name(ca.certificate().getSubjectX500Principal().getName()),
                        new BigInteger(64, new SecureRandom()),
                        Date.from(notBefore),
                        Date.from(notAfter),
                        new X500Name("CN=" + commonName),
                        subjectPublicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(ca.certificate()));
        if (!sans.isEmpty()) {
            final var names = new ArrayList<GeneralName>();
            for (final var san : sans) {
                names.add(new GeneralName(GeneralName.dNSName, san));
            }
            builder.addExtension(
                    Extension.subjectAlternativeName,
                    false,
                    GeneralNames.getInstance(
                            new DERSequence(names.toArray(new GeneralName[0]))));
        }

        final var signer =
                new JcaContentSignerBuilder("SHA256WITHECDSA")
                        .build(ca.keyPair().getPrivate());
        final var certificate =
                new JcaX509CertificateConverter()
                        .setProvider(BC)
                        .getCertificate(builder.build(signer));
        return new Leaf(certificate, encodedChain(certificate, ca.certificate()));
    }

    /**
     * A PKCS#12 keystore under alias {@code issuer}, holding a freshly generated key and a
     * leaf-to-root chain rooted in a CA generated for this call.
     */
    public static Pkcs12 pkcs12(final String commonName, final String... sans) throws Exception {
        final var ca = ca(commonName + " Root CA");
        final var keyPair = ecKeyPair();
        final var leaf = leaf(ca, keyPair.getPublic(), commonName, List.of(sans));
        final var password = new BigInteger(128, new SecureRandom()).toString(16);

        final var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setEntry(
                "issuer",
                new KeyStore.PrivateKeyEntry(
                        keyPair.getPrivate(),
                        new Certificate[] {leaf.certificate(), ca.certificate()}),
                new KeyStore.PasswordProtection(password.toCharArray()));

        final var output = new ByteArrayOutputStream();
        keyStore.store(output, password.toCharArray());
        return new Pkcs12(output.toByteArray(), password, leaf.encodedChain());
    }

    /** Base64-encodes certificates in the order given, leaf first. */
    public static List<String> encodedChain(final X509Certificate... chain) {
        return Arrays.stream(chain)
                .map(
                        certificate -> {
                            try {
                                return Base64.getEncoder()
                                        .encodeToString(certificate.getEncoded());
                            } catch (Exception exception) {
                                throw new IllegalStateException(
                                        "Could not encode test certificate", exception);
                            }
                        })
                .toList();
    }
}
