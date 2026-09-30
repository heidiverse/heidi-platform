// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.LocalDevelopmentTrustSeed;
import org.heidiverse.heidi.entity.service.TenantService;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Mints a throw-away certificate authority on the developer's machine and seeds the local issuer
 * with it, so issue-then-verify closes locally without an external CA on a Trusted List.
 *
 * <p>Gated on {@code heidi.platform.dev.generate-ca}, and on nothing else: it generates a CA private key,
 * so it stays off until someone says otherwise, and saying so is one setting. It used to also
 * require the {@code local} profile, which protected nothing - that profile's own properties file
 * is what sets the flag, so the two opened together - while making the CA unreachable from a test
 * deployment, which cannot run {@code local} without also taking its localhost URLs.
 *
 * <p>Two ways in. On a developer's machine the CA is minted on demand and cached at {@code
 * heidi.platform.dev.ca-keystore}, under {@code .local/} by default - never into {@code src/main/resources},
 * which is how the committed keystore this replaces ended up inside the jar. A deployment instead
 * hands one over through {@code heidi.platform.dev.ca-keystore-base64}, which is read and never written: a
 * pod filesystem does not survive the pod, and a CA that regenerated itself on the next start would
 * leave every credential issued under the last one unverifiable.
 */
@ConditionalOnProperty(name = "heidi.platform.dev.generate-ca", havingValue = "true")
@Configuration
public class PlatformApiLocalDevelopmentCaConfig {
    private static final String DEVELOPMENT_CA_NAME = "Heidi Development Root CA";
    private static final String DEVELOPMENT_CA_SUBJECT = "CN=" + DEVELOPMENT_CA_NAME;

    private static final Logger logger =
            LoggerFactory.getLogger(PlatformApiLocalDevelopmentCaConfig.class);

    private static final String ALIAS = "dev-ca";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * The CA as a bean rather than a start-up local, so {@link IssuerService} can certify keys
     * added later - through the Cockpit or an API call - with the same root it seeded at boot.
     */
    @Bean
    LocalDevelopmentTrustSeed localDevelopmentCaSeed(
            @Value("${heidi.platform.dev.ca-keystore:.local/dev-ca.p12}") String keystorePath,
            @Value("${heidi.platform.dev.ca-keystore-base64:}") String keystoreBase64,
            @Value("${heidi.platform.dev.ca-password:heidi-local-dev}") String keystorePassword,
            @Value("${heidi.platform.dev.ca-validity:P90D}") Duration validity,
            @Value("${heidi.issuer.public-base-url}") String issuerBaseUrl,
            @Value("${heidi.verifier.public-base-url:}") String verifierBaseUrl)
            throws Exception {
        final var ca =
                (keystoreBase64.isBlank()
                                ? loadOrCreate(
                                        Path.of(keystorePath),
                                        keystorePassword,
                                        validity,
                                        issuerBaseUrl)
                                : handedOver(keystoreBase64, keystorePassword, issuerBaseUrl))
                        .withVerifier(verifierBaseUrl);
        logger.info(
                "Local development CA: {} (SHA-256 {}), valid until {}",
                ca.certificate().getSubjectX500Principal().getName(),
                fingerprint(ca.certificate()),
                ca.certificate().getNotAfter().toInstant());
        return ca;
    }

    @Bean
    @Order(1)
    ApplicationRunner localDevelopmentCa(
            IssuerService issuerService,
            TenantService tenantService,
            LocalDevelopmentTrustSeed ca,
            @Value("${heidi.platform.local.issuer-slug:acme}") String issuerSlug,
            @Value("${heidi.platform.local.tenant-id:acme}") String tenantId,
            @Value("${heidi.platform.local.tenant-display-name:Acme}") String tenantName,
            @Value("${heidi.platform.local.issuer-display-name:Acme Digital Identity}")
                    String issuerName,
            @Value("${heidi.issuer.public-base-url}") String issuerBaseUrl,
            @Value("${heidi.platform.local.trust-systems:}") String trustSystems) {
        return args -> {
            // The no-security profile normally creates this tenant first, but the CA
            // bootstrap is also deliberately usable with OIDC/AppProxy enabled.
            // Keep the issuer's foreign-key prerequisite local to this bootstrap.
            if (tenantService.getTenant(tenantId).isEmpty()) {
                var request = new TenantRequest();
                request.setDisplayName(tenantName);
                request.setIssuerIds(List.of());
                tenantService.upsertTenant(tenantId, request);
            }
            var localIssuer = issuerService.ensureLocalDevelopmentIssuer(
                    tenantId, issuerSlug, issuerName);
            var request = new TenantRequest();
            request.setIssuerIds(List.of(localIssuer.getId()));
            tenantService.upsertTenant(tenantId, request);
            issuerService.seedLocalDevelopmentTrust(
                    issuerSlug,
                    issuerBaseUrl,
                    ca,
                    PlatformApiNoSecurityConfig.parseTrustSystems(trustSystems));
        };
    }

    /**
     * Loads the cached CA, or generates one when there is none or the cached one has expired.
     *
     * <p>Cached rather than regenerated per boot because the development database survives
     * restarts: a fresh CA on every start would silently invalidate credentials issued in an
     * earlier session. Regenerating only on expiry means staleness surfaces here, at startup,
     * rather than halfway through a wallet flow.
     */
    private static DevelopmentCa loadOrCreate(
            final Path keystorePath,
            final String password,
            final Duration validity,
            final String issuerIdentifier)
            throws Exception {
        final var existing = load(keystorePath, password, issuerIdentifier);
        if (existing != null) return existing;

        final var created = generate(validity, issuerIdentifier);
        store(keystorePath, password, created);
        logger.info("Generated a new local development CA at {}", keystorePath.toAbsolutePath());
        return created;
    }

    /**
     * The CA a deployment hands over, base64 PKCS#12, instead of leaving one on a pod's
     * filesystem where a replacement pod would lose it and mint another. Nothing is written and
     * nothing is regenerated: an unusable keystore fails start-up rather than quietly seeding a
     * new root that credentials already issued do not chain to.
     */
    private static DevelopmentCa handedOver(
            final String keystore, final String password, final String issuerIdentifier)
            throws Exception {
        try (InputStream input =
                new ByteArrayInputStream(Base64.getDecoder().decode(keystore.trim()))) {
            return read(input, password, issuerIdentifier);
        }
    }

    private static DevelopmentCa read(
            final InputStream input, final String password, final String issuerIdentifier)
            throws Exception {
        final var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(input, password.toCharArray());
        final var certificate = (X509Certificate) keyStore.getCertificate(ALIAS);
        final var privateKey = (PrivateKey) keyStore.getKey(ALIAS, password.toCharArray());
        if (certificate == null || privateKey == null) {
            throw new IllegalStateException("The keystore holds no key pair under alias " + ALIAS);
        }
        certificate.checkValidity();
        return new DevelopmentCa(privateKey, certificate, issuerIdentifier);
    }

    private static DevelopmentCa load(
            final Path keystorePath, final String password, final String issuerIdentifier) {
        if (!Files.isRegularFile(keystorePath)) return null;
        try (InputStream input = Files.newInputStream(keystorePath)) {
            var existing = read(input, password, issuerIdentifier);
            var subject = new JcaX509CertificateHolder(existing.certificate()).getSubject();
            if (!new X500Name(DEVELOPMENT_CA_SUBJECT).equals(subject)) {
                throw new IllegalStateException("certificate subject is " + subject);
            }
            return existing;
        } catch (Exception exception) {
            logger.info(
                    "Replacing the local development CA at {}: {}",
                    keystorePath.toAbsolutePath(),
                    exception.getMessage());
            return null;
        }
    }

    private static DevelopmentCa generate(final Duration validity, final String issuerIdentifier)
            throws Exception {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        final KeyPair keyPair = generator.generateKeyPair();

        final var notBefore = Instant.now().minus(Duration.ofHours(1));
        final var notAfter = notBefore.plus(validity);
        final var name = new X500Name(DEVELOPMENT_CA_SUBJECT);
        final var builder =
                new JcaX509v3CertificateBuilder(
                        name,
                        new BigInteger(64, new SecureRandom()),
                        Date.from(notBefore),
                        Date.from(notAfter),
                        name,
                        keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        final var certificate =
                new JcaX509CertificateConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .getCertificate(
                                builder.build(
                                        new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                                                .build(keyPair.getPrivate())));
        return new DevelopmentCa(keyPair.getPrivate(), certificate, issuerIdentifier);
    }

    private static void store(
            final Path keystorePath, final String password, final DevelopmentCa ca)
            throws Exception {
        final var parent = keystorePath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        final var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setEntry(
                ALIAS,
                new KeyStore.PrivateKeyEntry(
                        ca.privateKey(), new Certificate[] {ca.certificate()}),
                new KeyStore.PasswordProtection(password.toCharArray()));
        try (OutputStream output = Files.newOutputStream(keystorePath)) {
            keyStore.store(output, password.toCharArray());
        }
    }

    private static String fingerprint(final X509Certificate certificate) throws Exception {
        return HexFormat.ofDelimiter(":")
                .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    /** The generated CA, in the shape {@link IssuerService} consumes it. */
    private record DevelopmentCa(
            PrivateKey privateKey,
            X509Certificate certificate,
            String issuerIdentifier,
            String verifierIdentifier)
            implements LocalDevelopmentTrustSeed {

        private DevelopmentCa(
                PrivateKey privateKey, X509Certificate certificate, String issuerIdentifier) {
            this(privateKey, certificate, issuerIdentifier, null);
        }

        /**
         * The same CA, also naming the verifier's host in every leaf. A verifier identity signs
         * {@code x509_san_dns} requests whose client_id is that host, and a wallet rejects a leaf
         * that does not name it.
         */
        private DevelopmentCa withVerifier(final String verifierBaseUrl) {
            return new DevelopmentCa(privateKey, certificate, issuerIdentifier, verifierBaseUrl);
        }

        @Override
        public String rootCertificate() {
            try {
                return Base64.getEncoder().encodeToString(certificate.getEncoded());
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not encode the local development CA certificate", exception);
            }
        }

        @Override
        public List<String> certificateChain(
                final String subjectName,
                final String issuer,
                final byte[] subjectPublicKeyInfo) throws Exception {
            final var holder = new JcaX509CertificateHolder(certificate);
            // The leaf never outlives the CA that signed it, so an expired CA fails chain
            // validation on the anchor rather than leaving a leaf that verifies against nothing.
            final var notBefore = Instant.now().minus(Duration.ofHours(1));
            final var notAfter =
                    certificate.getNotAfter().toInstant().isBefore(notBefore.plus(Duration.ofDays(90)))
                            ? certificate.getNotAfter().toInstant()
                            : notBefore.plus(Duration.ofDays(90));

            final var builder =
                    new X509v3CertificateBuilder(
                            holder.getSubject(),
                            new BigInteger(64, new SecureRandom()),
                            Date.from(notBefore),
                            Date.from(notAfter),
                            new X500Name("CN=" + subjectName + ", O=" + subjectName + ", C=CH"),
                            SubjectPublicKeyInfo.getInstance(subjectPublicKeyInfo));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(
                    Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(
                    Extension.subjectAlternativeName, false, subjectAlternativeNames(issuer));

            final var leaf =
                    new JcaX509CertificateConverter()
                            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                            .getCertificate(
                                    builder.build(
                                            new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                                                    .build(privateKey)));
            return List.of(
                    Base64.getEncoder().encodeToString(leaf.getEncoded()),
                    Base64.getEncoder().encodeToString(certificate.getEncoded()));
        }

        /** URL issuer identifiers bind to their DNS host; other identifiers bind as URIs. */
        private GeneralNames subjectAlternativeNames(final String issuer) {
            final var issuerName = issuerSan(issuer);
            final var verifierHost =
                    verifierIdentifier == null || verifierIdentifier.isBlank()
                            ? null
                            : URI.create(verifierIdentifier).getHost();
            final var verifierIsSeparate =
                    verifierHost != null && !verifierHost.equals(issuerName.getName().toString());
            if (!verifierIsSeparate) return new GeneralNames(issuerName);

            return new GeneralNames(
                    new GeneralName[] {issuerName, new GeneralName(GeneralName.dNSName, verifierHost)});
        }

        private static GeneralName issuerSan(final String issuer) {
            final var host = URI.create(issuer).getHost();
            return host == null
                    ? new GeneralName(GeneralName.uniformResourceIdentifier, issuer)
                    : new GeneralName(GeneralName.dNSName, host);
        }
    }
}
