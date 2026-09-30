// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import static uniffi.heidi_signing.Heidi_signing_jvmKt.publicJwkFromDer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.impl.AESKW;
import com.nimbusds.jose.crypto.impl.ConcatKDF;
import com.nimbusds.jose.crypto.impl.ECDH;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECPoint;
import java.security.spec.ECParameterSpec;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.heidiverse.heidi.shared.signing.ProviderHealth;
import org.heidiverse.heidi.shared.signing.SigningKeyCreator;
import org.heidiverse.heidi.shared.signing.SigningKeyDeleter;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyImporter;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.heidiverse.heidi.shared.signing.SigningContentKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest;
import uniffi.heidi_signing.JoseKeyPair;
import uniffi.heidi_signing.Store;
import uniffi.heidi_signing.StoreObject;

/**
 * Software implementation of the signing contract.
 *
 * <p>The signing primitive is the same Rust implementation used by the existing issuer. Private
 * key bytes are retained only by this provider and are never put into a {@link SigningKeyRef}.
 * The default constructor deliberately keeps the store in memory. A deployment can instead
 * supply a durable store, or bootstrap one key from a mounted PKCS#12 file at startup.
 */
public final class SoftwareSigningKeyProvider
        implements SigningKeyProvider,
                SigningKeyCreator,
                SigningKeyImporter,
                SigningKeyDeleter,
                SigningContentKeyProvider,
                AutoCloseable {
    public static final String SCHEME = "software";

    private static final List<String> SUPPORTED_ALGORITHMS = List.of(
            "ES256", "ES384", "ES512", "EdDSA", "PS256", "PS384", "PS512",
            "RS256", "RS384", "RS512", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private static final List<String> DIGEST_ALGORITHMS = List.of(
            "ES256", "ES384", "ES512", "PS256", "PS384", "PS512",
            "RS256", "RS384", "RS512");

    private static final List<String> CONTENT_KEY_ALGORITHMS = List.of(
            "ECDH-ES", "ECDH-ES+A128KW", "ECDH-ES+A192KW", "ECDH-ES+A256KW",
            "RSA-OAEP-256");

    private static final Map<String, String> DIGEST_NAMES = Map.ofEntries(
            Map.entry("ES256", "SHA-256"),
            Map.entry("ES384", "SHA-384"),
            Map.entry("ES512", "SHA-512"),
            Map.entry("PS256", "SHA-256"),
            Map.entry("PS384", "SHA-384"),
            Map.entry("PS512", "SHA-512"),
            Map.entry("RS256", "SHA-256"),
            Map.entry("RS384", "SHA-384"),
            Map.entry("RS512", "SHA-512"));

    private final ConcurrentMap<String, KeyMaterial> keys = new ConcurrentHashMap<>();
    private final SoftwareSigningKeyStore keyStore;
    private final StoreObject signer;

    public SoftwareSigningKeyProvider() {
        this(new InMemorySoftwareSigningKeyStore());
    }

    /** Creates a provider backed by the supplied key store. */
    public SoftwareSigningKeyProvider(SoftwareSigningKeyStore keyStore) {
        this.keyStore = Objects.requireNonNull(keyStore, "Software signing key store must not be null");
        this.signer = new StoreObject(new Store() {
            @Override
            public byte[] getSecretKey(String alias) {
                var material = keys.get(alias);
                return material == null ? null : material.privateKey().clone();
            }

            @Override
            public byte[] getCertificate(String alias) {
                var material = keys.get(alias);
                return material == null ? null : material.publicKey().clone();
            }

            @Override
            public String getKeyAlgorithm(String alias) {
                var material = keys.get(alias);
                return material == null ? null : material.ref().algorithm();
            }
        });
        loadPersistedKeys();
    }

    /**
     * Creates a software provider and imports one PKCS#12 key when the backing store is empty.
     *
     * <p>This is intended for deployments that mount a fixed keystore as a Kubernetes Secret. The
     * keystore is read once during startup; it is not watched and changes require a restart. The
     * key is persisted as a normal, deletable key when the backing store has no keys. If persisted
     * keys already exist, the PKCS#12 file is ignored. If {@code alias} is blank, the keystore
     * must contain exactly one private-key entry. If {@code keyId} is blank, the selected entry
     * alias is used as the local key ID.
     *
     * @param path mounted PKCS#12 file
     * @param password keystore and private-key password; it is copied only for the duration of
     *     loading
     * @param alias private-key entry alias, or blank when the file has exactly one such entry
     * @param keyId local key ID, or blank to use the entry alias
     * @param algorithm JOSE algorithm to use for this key (required because an RSA key alone does
     *     not distinguish RS* from PS*)
     * @return a provider containing the persisted or bootstrapped key
     * @throws SigningKeyException if the keystore cannot be read or the entry is unusable
     */
    public static SoftwareSigningKeyProvider fromPkcs12(
            Path path, char[] password, String alias, String keyId, String algorithm) {
        return fromPkcs12(path, password, alias, keyId, algorithm, new InMemorySoftwareSigningKeyStore());
    }

    /**
     * Creates a provider backed by {@code keyStore} and imports a PKCS#12 key only when the store
     * is empty. Persisted keys are loaded first; the PKCS#12 key is then stored as a normal,
     * deletable key only for initial bootstrapping.
     */
    public static SoftwareSigningKeyProvider fromPkcs12(
            Path path,
            char[] password,
            String alias,
            String keyId,
            String algorithm,
            SoftwareSigningKeyStore keyStore) {
        if (path == null) {
            throw new SigningKeyException("PKCS#12 path must not be null");
        }
        if (password == null) {
            throw new SigningKeyException("PKCS#12 password must not be null");
        }
        var provider = new SoftwareSigningKeyProvider(keyStore);
        try {
            if (provider.keys.isEmpty()) {
                provider.loadPkcs12(path, password, alias, keyId, algorithm);
            }
            return provider;
        } catch (RuntimeException exception) {
            provider.close();
            throw exception;
        }
    }

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public List<String> supportedAlgorithms() {
        return SUPPORTED_ALGORITHMS;
    }

    @Override
    public List<String> digestSigningAlgorithms() {
        return DIGEST_ALGORITHMS;
    }

    @Override
    public SigningKeyRef resolve(String keyUri) {
        requireSoftwareUri(keyUri);
        var material = findMaterial(keyUri);
        if (material == null) {
            throw new SigningKeyException("Unknown software signing key: " + keyUri);
        }
        return material.ref();
    }

    @Override
    public SigningKeyRef createKey(String keyId, String algorithm) {
        return createKey(keyId, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    @Override
    public SigningKeyRef createKey(
            String keyId, String algorithm, Set<SigningKeyUsage> usages) {
        requireKeyId(keyId);
        requireSupported(algorithm);
        var effectiveUsages = validUsages(usages);
        var uri = keyUri(keyId);
        try (var keyPair = JoseKeyPair.Companion.generate(algorithm)) {
            var material = material(uri, keyId, algorithm, keyPair, effectiveUsages);
            addKey(material);
            return material.ref();
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SigningKeyException("Could not generate " + algorithm + " signing key", exception);
        }
    }

    @Override
    public SigningKeyRef importKey(String keyId, String privateJwk, String algorithm) {
        return importKey(keyId, privateJwk, algorithm, Set.of(SigningKeyUsage.SIGN));
    }

    @Override
    public SigningKeyRef importKey(
            String keyId, String privateJwk, String algorithm, Set<SigningKeyUsage> usages) {
        requireKeyId(keyId);
        requireSupported(algorithm);
        var effectiveUsages = validUsages(usages);
        if (privateJwk == null || privateJwk.isBlank()) {
            throw new SigningKeyException("Private JWK must not be blank");
        }
        var uri = keyUri(keyId);
        byte[] privateKey = null;
        try {
            JWK jwk = JWK.parse(privateJwk);
            privateKey = toPrivateKey(jwk).getPrivate().getEncoded();
            if (privateKey == null || privateKey.length == 0) {
                throw new SigningKeyException("Private JWK has no encodable private key");
            }
            try (var keyPair = new JoseKeyPair(privateKey, algorithm)) {
                var material = material(uri, keyId, algorithm, keyPair, effectiveUsages);
                addKey(material);
                return material.ref();
            }
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SigningKeyException("Could not import " + algorithm + " signing key", exception);
        } finally {
            if (privateKey != null) {
                Arrays.fill(privateKey, (byte) 0);
            }
        }
    }

    @Override
    public void deleteKey(SigningKeyRef ref) {
        if (ref == null) {
            throw new SigningKeyException("Signing key reference must not be null");
        }
        synchronized (keys) {
            var material = keys.get(ref.uri());
            if (material == null) {
                throw new SigningKeyException("Unknown software signing key: " + ref.uri());
            }
            if (!material.deletable()) {
                throw new SigningKeyException("The signing key is not deletable: " + ref.uri());
            }
            keyStore.delete(keyId(ref.uri()));
            if (!keys.remove(ref.uri(), material)) {
                throw new SigningKeyException(
                        "Signing key changed while it was being deleted: " + ref.uri());
            }
            material.clearPrivateKey();
        }
    }

    @Override
    public byte[] sign(SigningKeyRef ref, byte[] message) {
        var material = checkedMaterial(ref);
        requireUsage(material, SigningKeyUsage.SIGN);
        if (message == null) {
            throw new SigningKeyException("Message must not be null");
        }
        try {
            return signer.sign(material.ref().uri(), message.clone());
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not sign with " + material.ref().uri(), exception);
        }
    }

    @Override
    public byte[] signDigest(SigningKeyRef ref, byte[] digest, String digestAlgorithm) {
        var material = checkedMaterial(ref);
        requireUsage(material, SigningKeyUsage.SIGN);
        var expected = DIGEST_NAMES.get(material.ref().algorithm());
        if (expected == null) {
            throw new SigningKeyException(
                    "Provider '" + SCHEME + "' cannot sign a digest with " + material.ref().algorithm());
        }
        if (!expected.equalsIgnoreCase(digestAlgorithm)) {
            throw new SigningKeyException(
                    "Digest algorithm " + digestAlgorithm + " does not match " + expected);
        }
        if (digest == null) {
            throw new SigningKeyException("Digest must not be null");
        }
        try {
            return signer.signDigest(material.ref().uri(), digest.clone());
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not sign digest with " + material.ref().uri(), exception);
        }
    }

    @Override
    public List<String> contentKeyAlgorithms() {
        return CONTENT_KEY_ALGORITHMS;
    }

    @Override
    public byte[] contentKey(SigningKeyRef ref, SigningContentKeyRequest request) {
        var material = checkedMaterial(ref);
        if (request == null || request.algorithm() == null
                || !CONTENT_KEY_ALGORITHMS.contains(request.algorithm())) {
            throw new SigningKeyException("Unsupported content-key algorithm");
        }
        if (request.contentEncryption() == null
                || contentKeyLength(request.contentEncryption()) == 0) {
            throw new SigningKeyException("Unsupported content encryption method");
        }
        requireUsage(material, request.algorithm().startsWith("ECDH-ES")
                ? SigningKeyUsage.KEY_AGREEMENT : SigningKeyUsage.UNWRAP);
        try {
            if (request.algorithm().startsWith("ECDH-ES")) {
                return deriveEcdhContentKey(material, request);
            }
            return unwrapRsaContentKey(material, request);
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SigningKeyException("Could not derive the JWE content key", exception);
        }
    }

    private static byte[] deriveEcdhContentKey(
            KeyMaterial material, SigningContentKeyRequest request) throws Exception {
        var publicKey = JWK.parse(material.ref().publicJwk());
        if (!(publicKey instanceof ECKey ecKey)
                || !com.nimbusds.jose.jwk.Curve.P_256.equals(ecKey.getCurve())) {
            throw new SigningKeyException("ECDH content-key operations require a P-256 key");
        }
        if (request.ephemeralPublicJwk() == null || request.ephemeralPublicJwk().isBlank()) {
            throw new SigningKeyException("ECDH content-key operation requires epk");
        }
        var ephemeral = JWK.parse(request.ephemeralPublicJwk());
        if (!(ephemeral instanceof ECKey ephemeralKey)
                || ephemeralKey.isPrivate()
                || !com.nimbusds.jose.jwk.Curve.P_256.equals(ephemeralKey.getCurve())) {
            throw new SigningKeyException("epk must be an EC P-256 public key");
        }
        validatePoint(ephemeralKey.toECPublicKey().getW(), ephemeralKey.toECPublicKey().getParams());
        var privateKey = ecPrivateKey(material, ecKey);

        var algorithm = JWEAlgorithm.parse(request.algorithm());
        var encryption = EncryptionMethod.parse(request.contentEncryption());
        var header = new JWEHeader.Builder(algorithm, encryption)
                .ephemeralPublicKey(ephemeralKey.toPublicJWK())
                .agreementPartyUInfo(base64(request.agreementPartyUInfo()))
                .agreementPartyVInfo(base64(request.agreementPartyVInfo()))
                .build();
        var shared = ECDH.deriveSharedSecret(ephemeralKey.toECPublicKey(), privateKey, null);
        var derived = ECDH.deriveSharedKey(header, shared, new ConcatKDF("SHA-256"));
        if ("ECDH-ES".equals(request.algorithm())) {
            requireLength(derived.getEncoded(), contentKeyLength(request.contentEncryption()));
            requireEmptyEncryptedKey(request.encryptedKey());
            return derived.getEncoded();
        }
        var encryptedKey = request.encryptedKey();
        if (encryptedKey == null || encryptedKey.length == 0) {
            throw new SigningKeyException("ECDH key-wrap operation requires encrypted_key");
        }
        return AESKW.unwrapCEK(derived, encryptedKey, null).getEncoded();
    }

    private static byte[] unwrapRsaContentKey(
            KeyMaterial material, SigningContentKeyRequest request) throws Exception {
        var publicKey = JWK.parse(material.ref().publicJwk());
        if (!(publicKey instanceof RSAKey)) {
            throw new SigningKeyException("RSA content-key operations require an RSA key");
        }
        var privateKey = privateKey(material.privateKey(), "RSA");
        if (!(privateKey instanceof RSAPrivateKey)) {
            throw new SigningKeyException("Signing key has no RSA private key");
        }
        var encryptedKey = request.encryptedKey();
        if (encryptedKey == null || encryptedKey.length == 0) {
            throw new SigningKeyException("RSA key-wrap operation requires encrypted_key");
        }
        var cipher = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                privateKey,
                new javax.crypto.spec.OAEPParameterSpec(
                        "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256,
                        javax.crypto.spec.PSource.PSpecified.DEFAULT));
        var contentKey = cipher.doFinal(encryptedKey);
        requireLength(contentKey, contentKeyLength(request.contentEncryption()));
        return contentKey;
    }

    private static PrivateKey privateKey(byte[] encoded, String algorithm) throws Exception {
        return KeyFactory.getInstance(algorithm)
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static ECPrivateKey ecPrivateKey(KeyMaterial material, ECKey publicKey)
            throws Exception {
        var publicEc = publicKey.toECPublicKey();
        var scalar = new java.math.BigInteger(1, ecPrivateScalar(material.privateKey()));
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                new java.security.spec.ECPrivateKeySpec(scalar, publicEc.getParams()));
    }

    /** Rejects invalid or exceptional peer points before the private key participates in ECDH. */
    private static void validatePoint(ECPoint point, ECParameterSpec parameters) {
        if (point == null || ECPoint.POINT_INFINITY.equals(point)) {
            throw new SigningKeyException("epk must not be the point at infinity");
        }
        var curve = parameters.getCurve();
        if (!(curve.getField() instanceof java.security.spec.ECFieldFp field)) {
            throw new SigningKeyException("epk must use a prime-field P-256 curve");
        }
        var p = field.getP();
        var x = point.getAffineX();
        var y = point.getAffineY();
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            throw new SigningKeyException("epk is outside the P-256 field");
        }
        var left = y.modPow(BigInteger.TWO, p);
        var right = x.modPow(BigInteger.valueOf(3), p)
                .add(curve.getA().multiply(x)).add(curve.getB()).mod(p);
        if (!left.equals(right)) {
            throw new SigningKeyException("epk is not on the P-256 curve");
        }
    }

    /** josekit emits SEC1 ECPrivateKey DER; accept PKCS#8 as well for imported material. */
    static byte[] ecPrivateScalar(byte[] encoded) {
        if (encoded == null || encoded.length < 8 || encoded[0] != 0x30) {
            throw new SigningKeyException("EC private key has an invalid DER encoding");
        }
        var sequence = readDerLength(encoded, 1);
        var pos = sequence.valueStart;
        if (encoded[pos++] != 0x02) {
            throw new SigningKeyException("EC private key has no version");
        }
        pos = readDerLength(encoded, pos).valueEnd;
        var pkcs8 = false;
        if (encoded[pos] == 0x30) {
            var algorithm = readDerLength(encoded, pos + 1);
            pos = algorithm.valueEnd;
            pkcs8 = true;
        }
        if (encoded[pos++] != 0x04) {
            throw new SigningKeyException("EC private key has no private octets");
        }
        var octets = readDerLength(encoded, pos);
        var value = java.util.Arrays.copyOfRange(encoded, octets.valueStart, octets.valueEnd);
        if (pkcs8) return ecPrivateScalar(value);
        return value;
    }

    private static DerLength readDerLength(byte[] encoded, int tagEnd) {
        if (tagEnd >= encoded.length) {
            throw new SigningKeyException("EC private key has an invalid DER length");
        }
        var first = encoded[tagEnd] & 0xff;
        if ((first & 0x80) == 0) {
            var start = tagEnd + 1;
            return new DerLength(start, start + first);
        }
        var count = first & 0x7f;
        if (count == 0 || count > 4 || tagEnd + 1 + count > encoded.length) {
            throw new SigningKeyException("EC private key has an invalid DER length");
        }
        var length = 0;
        for (var index = 0; index < count; index++) {
            length = (length << 8) | (encoded[tagEnd + 1 + index] & 0xff);
        }
        var start = tagEnd + 1 + count;
        if (length < 0 || start + length > encoded.length) {
            throw new SigningKeyException("EC private key has an invalid DER length");
        }
        return new DerLength(start, start + length);
    }

    private record DerLength(int valueStart, int valueEnd) {}

    private static Base64URL base64(String value) {
        return value == null || value.isBlank() ? null : new Base64URL(value);
    }

    private static int contentKeyLength(String method) {
        return switch (method) {
            case "A128GCM" -> 16;
            case "A192GCM" -> 24;
            case "A256GCM" -> 32;
            case "A128CBC-HS256" -> 32;
            case "A192CBC-HS384" -> 48;
            case "A256CBC-HS512" -> 64;
            default -> 0;
        };
    }

    private static void requireLength(byte[] value, int expected) {
        if (value == null || value.length != expected) {
            throw new SigningKeyException("Derived content key has an invalid length");
        }
    }

    private static void requireEmptyEncryptedKey(byte[] value) {
        if (value != null && value.length > 0) {
            throw new SigningKeyException("ECDH-ES must not carry encrypted_key");
        }
    }

    @Override
    public ProviderHealth health() {
        return ProviderHealth.up(0);
    }

    @Override
    public void close() {
        keys.values().forEach(KeyMaterial::clearPrivateKey);
        keys.clear();
        signer.close();
        keyStore.close();
    }

    private void loadPersistedKeys() {
        for (var stored : keyStore.load()) {
            var material = storedMaterial(stored);
            if (keys.putIfAbsent(material.ref().uri(), material) != null) {
                material.clearPrivateKey();
                throw new SigningKeyException(
                        "Duplicate persisted signing key: " + material.ref().uri());
            }
        }
    }

    private void addKey(KeyMaterial material) {
        synchronized (keys) {
            var uri = material.ref().uri();
            if (keys.containsKey(uri)) {
                throw new SigningKeyException("A software signing key already exists: " + uri);
            }
            keyStore.save(new StoredSoftwareSigningKey(
                    keyId(uri),
                    material.ref().algorithm(),
                    material.privateKey(),
                    material.publicKey(),
                    material.deletable(),
                    material.ref().usages()));
            keys.put(uri, material);
        }
    }

    private KeyMaterial checkedMaterial(SigningKeyRef ref) {
        if (ref == null) {
            throw new SigningKeyException("Signing key reference must not be null");
        }
        requireSoftwareUri(ref.uri());
        var material = findMaterial(ref.uri());
        if (material == null) {
            throw new SigningKeyException("Unknown software signing key: " + ref.uri());
        }
        if (!material.ref().algorithm().equals(ref.algorithm())) {
            throw new SigningKeyException("Signing key algorithm does not match the stored key");
        }
        return material;
    }

    private KeyMaterial findMaterial(String uri) {
        var material = keys.get(uri);
        if (material != null) return material;

        // Another replica may have persisted the key after this provider started.
        synchronized (keys) {
            material = keys.get(uri);
            if (material != null) return material;

            var stored = keyStore.find(keyId(uri)).orElse(null);
            if (stored == null) return null;

            material = storedMaterial(stored);
            keys.put(uri, material);
            return material;
        }
    }

    private static KeyMaterial storedMaterial(StoredSoftwareSigningKey stored) {
        var privateKey = stored.privateKey();
        var uri = keyUri(stored.keyId());
        try {
            requireKeyId(stored.keyId());
            requireSupported(stored.algorithm());
            try (var keyPair = new JoseKeyPair(privateKey, stored.algorithm())) {
                if (!Arrays.equals(stored.publicKey(), keyPair.publicKey())) {
                    throw new SigningKeyException(
                            "Persisted public key does not match private key: " + uri);
                }
                return material(
                        uri,
                        stored.keyId(),
                        stored.algorithm(),
                        privateKey,
                        stored.publicKey(),
                        stored.deletable(),
                        stored.usages());
            }
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SigningKeyException(
                    "Could not load persisted software signing key: " + stored.keyId(), exception);
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private static KeyMaterial material(
            String uri, String keyId, String algorithm, JoseKeyPair keyPair) throws Exception {
        return material(uri, keyId, algorithm, keyPair, Set.of(SigningKeyUsage.SIGN));
    }

    private static KeyMaterial material(
            String uri,
            String keyId,
            String algorithm,
            JoseKeyPair keyPair,
            Set<SigningKeyUsage> usages)
            throws Exception {
        var privateKey = keyPair.keyBytes();
        var publicKey = keyPair.publicKey();
        return material(uri, keyId, algorithm, privateKey, publicKey, true, usages);
    }

    private static KeyMaterial material(
            String uri,
            String keyId,
            String algorithm,
            byte[] privateKey,
            byte[] publicKey,
            boolean deletable,
            Set<SigningKeyUsage> usages)
            throws Exception {
        var publicJwk = publicJwkFromDer(publicKey, algorithm, keyId);
        return new KeyMaterial(
                new SigningKeyRef(uri, publicJwk, algorithm, usages), privateKey, publicKey, deletable);
    }

    private static Set<SigningKeyUsage> validUsages(Set<SigningKeyUsage> usages) {
        if (usages == null || usages.isEmpty()) {
            throw new SigningKeyException("Signing key usages must not be empty");
        }
        return Set.copyOf(usages);
    }

    private static void requireUsage(KeyMaterial material, SigningKeyUsage usage) {
        if (!material.ref().usages().contains(usage)) {
            throw new SigningKeyException(
                    "Signing key " + material.ref().uri() + " does not allow " + usage);
        }
    }

    private void loadPkcs12(
            Path path, char[] password, String configuredAlias, String configuredKeyId, String algorithm) {
        var normalizedAlias = normalize(configuredAlias);
        var keyId = normalize(configuredKeyId);
        var normalizedAlgorithm = normalize(algorithm);
        requireSupported(normalizedAlgorithm);

        try (InputStream input = Files.newInputStream(path)) {
            var keyStore = KeyStore.getInstance("PKCS12");
            var loadPassword = password.clone();
            try {
                keyStore.load(input, loadPassword);
            } finally {
                Arrays.fill(loadPassword, '\0');
            }
            var alias = selectAlias(keyStore, normalizedAlias);
            var keyPassword = password.clone();
            Key key;
            try {
                key = keyStore.getKey(alias, keyPassword);
            } finally {
                Arrays.fill(keyPassword, '\0');
            }
            if (!(key instanceof PrivateKey privateKey)) {
                throw new SigningKeyException("PKCS#12 entry is not a private key: " + alias);
            }
            var certificate = keyStore.getCertificate(alias);
            var privateKeyBytes = privateKey.getEncoded();
            if (privateKeyBytes == null || privateKeyBytes.length == 0) {
                throw new SigningKeyException("PKCS#12 private key is not encodable: " + alias);
            }
            var effectiveKeyId = keyId == null ? alias : keyId;
            requireKeyId(effectiveKeyId);
            var uri = keyUri(effectiveKeyId);

            try (var keyPair = new JoseKeyPair(privateKeyBytes, normalizedAlgorithm)) {
                var publicKeyBytes = certificate == null
                        ? keyPair.publicKey()
                        : certificate.getPublicKey().getEncoded();
                if (publicKeyBytes == null || publicKeyBytes.length == 0) {
                    throw new SigningKeyException(
                            "PKCS#12 public key is not encodable: " + alias);
                }
                if (certificate != null && !Arrays.equals(publicKeyBytes, keyPair.publicKey())) {
                    throw new SigningKeyException(
                            "PKCS#12 certificate does not match the private key: " + alias);
                }
                var material = material(
                        uri,
                        effectiveKeyId,
                        normalizedAlgorithm,
                        privateKeyBytes,
                        publicKeyBytes,
                        true,
                        Set.of(SigningKeyUsage.SIGN));
                addKey(material);
            } finally {
                Arrays.fill(privateKeyBytes, (byte) 0);
            }
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SigningKeyException("Could not load PKCS#12 software signing key", exception);
        }
    }

    private static String selectAlias(KeyStore keyStore, String configuredAlias) throws Exception {
        if (configuredAlias != null) {
            if (!keyStore.isKeyEntry(configuredAlias)) {
                throw new SigningKeyException(
                        "PKCS#12 does not contain a private-key entry named: " + configuredAlias);
            }
            return configuredAlias;
        }

        var aliases = new ArrayList<String>();
        Enumeration<String> entries = keyStore.aliases();
        while (entries.hasMoreElements()) {
            var alias = entries.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                aliases.add(alias);
            }
        }
        if (aliases.size() != 1) {
            throw new SigningKeyException(
                    "PKCS#12 must contain exactly one private-key entry when no alias is configured");
        }
        return aliases.getFirst();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static KeyPair toPrivateKey(JWK jwk) throws JOSEException {
        if (jwk instanceof ECKey ecKey) {
            return ecKey.toKeyPair();
        }
        if (jwk instanceof RSAKey rsaKey) {
            return rsaKey.toKeyPair();
        }
        if (jwk instanceof OctetKeyPair octetKeyPair) {
            return octetKeyPair.toKeyPair();
        }
        throw new SigningKeyException("Unsupported private JWK type: " + jwk.getClass().getSimpleName());
    }

    private static void requireSupported(String algorithm) {
        if (algorithm == null || !SUPPORTED_ALGORITHMS.contains(algorithm)) {
            throw new SigningKeyException("Unsupported signing algorithm: " + algorithm);
        }
    }

    private static void requireKeyId(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.contains("#") || keyId.contains("?")) {
            throw new SigningKeyException("Key ID must be non-empty and must not contain '?' or '#'");
        }
    }

    private static String keyUri(String keyId) {
        return SCHEME + "://" + keyId;
    }

    private static String keyId(String uri) {
        return uri.substring((SCHEME + "://").length());
    }

    private static void requireSoftwareUri(String uri) {
        if (uri == null || !uri.startsWith(SCHEME + "://")) {
            throw new SigningKeyException("Not a software signing key URI: " + uri);
        }
    }

    private static final class KeyMaterial {
        private final SigningKeyRef ref;
        private final byte[] privateKey;
        private final byte[] publicKey;
        private final boolean deletable;

        private KeyMaterial(
                SigningKeyRef ref, byte[] privateKey, byte[] publicKey, boolean deletable) {
            this.ref = ref;
            this.privateKey = privateKey.clone();
            this.publicKey = publicKey.clone();
            this.deletable = deletable;
        }

        private SigningKeyRef ref() {
            return ref;
        }

        private byte[] privateKey() {
            return privateKey;
        }

        private byte[] publicKey() {
            return publicKey;
        }

        private boolean deletable() {
            return deletable;
        }

        private void clearPrivateKey() {
            java.util.Arrays.fill(privateKey, (byte) 0);
        }
    }
}
