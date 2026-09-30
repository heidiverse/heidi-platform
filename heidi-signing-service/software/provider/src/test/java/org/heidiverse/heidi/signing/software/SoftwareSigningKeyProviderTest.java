// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import java.nio.file.Files;
import org.heidiverse.heidi.shared.testing.TestCertificates;
import org.heidiverse.heidi.shared.signing.SigningContentKeyRequest;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SoftwareSigningKeyProviderTest {
    @Test
    void createsResolvesAndSignsWithoutPuttingPrivateMaterialInReference() {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey("tenant-a/issuer", "ES256");

            assertEquals("software", ref.scheme());
            assertEquals("ES256", ref.algorithm());
            assertTrue(ref.publicJwk().contains("\"kty\""));
            assertNotNull(provider.resolve(ref.uri()));

            var signature = provider.sign(ref, new byte[] {1, 2, 3});
            assertNotNull(signature);
            assertEquals(64, signature.length, "ES256 must use JOSE R||S encoding");
        }
    }

    @Test
    void digestSigningIsLimitedToHashThenSignAlgorithmsAndChecksTheHashName() {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey("digest-key", "ES256");
            var digest = new byte[32];

            assertNotNull(provider.signDigest(ref, digest, "SHA-256"));
            assertThrows(
                    SigningKeyException.class,
                    () -> provider.signDigest(ref, digest, "SHA-512"));

            var pure = provider.createKey("pure-key", "EdDSA");
            assertThrows(
                    SigningKeyException.class,
                    () -> provider.signDigest(pure, digest, "SHA-256"));
        }
    }

    @Test
    void deletedKeysCannotBeResolvedOrUsed() {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey("delete-me", "EdDSA");
            provider.deleteKey(ref);

            assertThrows(SigningKeyException.class, () -> provider.resolve(ref.uri()));
            assertThrows(SigningKeyException.class, () -> provider.sign(ref, new byte[] {1}));
        }
    }

    @Test
    void signingDoesNotMutateCallerInput() {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey("copy-check", "EdDSA");
            var message = new byte[] {4, 5, 6};
            var before = message.clone();
            provider.sign(ref, message);
            assertArrayEquals(before, message);
        }
    }

    @Test
    void derivesTheContentKeyForEcdhJwe() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey(
                    "content-key", "ES256", java.util.Set.of(SigningKeyUsage.KEY_AGREEMENT));
            var recipient = ECKey.parse(ref.publicJwk()).toECKey();
            var jwe = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build(),
                    new Payload("payload"));
            jwe.encrypt(new ECDHEncrypter(recipient.toECPublicKey()));

            var contentKey = provider.contentKey(ref, new SigningContentKeyRequest(
                    JWEAlgorithm.ECDH_ES.getName(),
                    EncryptionMethod.A256GCM.getName(),
                    jwe.getHeader().getEphemeralPublicKey().toJSONString(),
                    null,
                    null,
                    null));

            assertEquals(32, contentKey.length);
        }
    }

    @Test
    void readsSec1ScalarStartingWithSequenceTag() {
        var scalar = new byte[32];
        scalar[0] = 0x30;
        var sec1 = new byte[39];
        sec1[0] = 0x30;
        sec1[1] = 0x25;
        sec1[2] = 0x02;
        sec1[3] = 0x01;
        sec1[4] = 0x01;
        sec1[5] = 0x04;
        sec1[6] = 0x20;
        System.arraycopy(scalar, 0, sec1, 7, scalar.length);

        assertArrayEquals(scalar, SoftwareSigningKeyProvider.ecPrivateScalar(sec1));
    }

    @Test
    void derivesTheFullCekForCbcHmacJwe() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey(
                    "content-key-cbc", "ES256", java.util.Set.of(SigningKeyUsage.KEY_AGREEMENT));
            var recipient = ECKey.parse(ref.publicJwk()).toECKey();
            var jwe = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256CBC_HS512).build(),
                    new Payload("payload"));
            jwe.encrypt(new ECDHEncrypter(recipient.toECPublicKey()));

            var contentKey = provider.contentKey(ref, new SigningContentKeyRequest(
                    JWEAlgorithm.ECDH_ES.getName(),
                    EncryptionMethod.A256CBC_HS512.getName(),
                    jwe.getHeader().getEphemeralPublicKey().toJSONString(),
                    null,
                    null,
                    null));

            assertEquals(64, contentKey.length);
        }
    }

    @Test
    void signingKeyCannotBeUsedForKeyAgreement() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey("signing-only", "ES256");
            assertThrows(SigningKeyException.class, () -> provider.contentKey(ref,
                    new SigningContentKeyRequest(
                            JWEAlgorithm.ECDH_ES.getName(), EncryptionMethod.A256GCM.getName(),
                            "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AA\",\"y\":\"AA\"}",
                            null, null, null)));
        }
    }

    @Test
    void rejectsAnEphemeralPointOutsideTheP256Curve() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey(
                    "invalid-epk", "ES256", java.util.Set.of(SigningKeyUsage.KEY_AGREEMENT));
            var recipient = ECKey.parse(ref.publicJwk()).toECKey();
            var jwe = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).build(),
                    new Payload("payload"));
            jwe.encrypt(new ECDHEncrypter(recipient.toECPublicKey()));
            var epk = jwe.getHeader().getEphemeralPublicKey().toPublicJWK().toJSONString();
            var invalid = epk.replace(
                    "\"x\":\"" + jwe.getHeader().getEphemeralPublicKey().toECKey().getX() + "\"",
                    "\"x\":\"" + Base64URL.encode(new byte[32]) + "\"");

            assertThrows(SigningKeyException.class, () -> provider.contentKey(ref,
                    new SigningContentKeyRequest(
                            JWEAlgorithm.ECDH_ES.getName(), EncryptionMethod.A256GCM.getName(),
                            invalid,
                            null, null, null)));
        }
    }

    @Test
    void unwrapsTheContentKeyForEcdhKeyWrapJwe() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var ref = provider.createKey(
                    "content-key-wrap", "ES256", java.util.Set.of(SigningKeyUsage.KEY_AGREEMENT));
            var recipient = ECKey.parse(ref.publicJwk()).toECKey();
            var jwe = new JWEObject(
                    new JWEHeader.Builder(
                            JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM).build(),
                    new Payload("payload"));
            jwe.encrypt(new ECDHEncrypter(recipient.toECPublicKey()));

            var contentKey = provider.contentKey(ref, new SigningContentKeyRequest(
                    JWEAlgorithm.ECDH_ES_A256KW.getName(),
                    EncryptionMethod.A256GCM.getName(),
                    jwe.getHeader().getEphemeralPublicKey().toJSONString(),
                    null,
                    null,
                    jwe.getEncryptedKey().decode()));

            assertEquals(32, contentKey.length);
        }
    }

    @Test
    void unwrapsTheContentKeyForRsaJwe() throws Exception {
        try (var provider = new SoftwareSigningKeyProvider()) {
            var original = new RSAKeyGenerator(2048).generate();
            var ref = provider.importKey(
                    "rsa-content-key", original.toJSONString(), "RS256",
                    java.util.Set.of(SigningKeyUsage.UNWRAP));
            var recipient = RSAKey.parse(ref.publicJwk()).toRSAKey();
            var jwe = new JWEObject(
                    new JWEHeader.Builder(
                            JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM).build(),
                    new Payload("payload"));
            jwe.encrypt(new RSAEncrypter(recipient.toRSAPublicKey()));

            var contentKey = provider.contentKey(ref, new SigningContentKeyRequest(
                    JWEAlgorithm.RSA_OAEP_256.getName(),
                    EncryptionMethod.A256GCM.getName(),
                    null,
                    null,
                    null,
                    jwe.getEncryptedKey().decode()));
            assertEquals(32, contentKey.length);
        }
    }

    @Test
    void importsPkcs12KeyAsNormalDeletableKey() throws Exception {
        var pkcs12 = TestCertificates.pkcs12("Static issuer");
        var path = Files.createTempFile("heidi-signing", ".p12");
        try {
            Files.write(path, pkcs12.keystore());
            try (var provider = SoftwareSigningKeyProvider.fromPkcs12(
                    path, pkcs12.password().toCharArray(), null, "fixed-issuer", "ES256")) {
                var ref = provider.resolve("software://fixed-issuer");

                assertEquals("ES256", ref.algorithm());
                assertTrue(ref.publicJwk().contains("\"kty\""));
                assertEquals(64, provider.sign(ref, new byte[] {1, 2, 3}).length);
                provider.deleteKey(ref);
                assertThrows(SigningKeyException.class, () -> provider.resolve(ref.uri()));
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void databaseStoreSurvivesProviderRestartAndDeletesPersistedKeys() throws Exception {
        var database = Files.createTempFile("heidi-signing", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            jdbc.execute(
                    """
                    CREATE TABLE t_signing_key
                    (
                        key_id TEXT PRIMARY KEY,
                        algorithm TEXT NOT NULL,
                        encrypted_private_key TEXT NOT NULL,
                        public_key TEXT NOT NULL,
                        encryption_salt TEXT NOT NULL UNIQUE,
                        deletable BOOLEAN NOT NULL DEFAULT TRUE,
                        usages TEXT NOT NULL DEFAULT 'SIGN'
                    )
                    """);

            var store = new DatabaseSoftwareSigningKeyStore(jdbc, "01".repeat(32));
            String uri;
            String publicJwk;
            try (var provider = new SoftwareSigningKeyProvider(store)) {
                var ref = provider.createKey("restart-me", "ES256");
                uri = ref.uri();
                publicJwk = ref.publicJwk();
                assertEquals(64, provider.sign(ref, new byte[] {1, 2, 3}).length);
            }

            var encrypted = jdbc.queryForObject(
                    "SELECT encrypted_private_key FROM t_signing_key WHERE key_id = ?",
                    String.class,
                    "restart-me");
            assertNotNull(encrypted);
            assertTrue(encrypted.startsWith("v2:"));

            try (var provider = new SoftwareSigningKeyProvider(
                    new DatabaseSoftwareSigningKeyStore(jdbc, "01".repeat(32)))) {
                var ref = provider.resolve(uri);
                assertEquals(publicJwk, ref.publicJwk());
                assertEquals(64, provider.sign(ref, new byte[] {4, 5, 6}).length);
                provider.deleteKey(ref);
            }
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM t_signing_key WHERE key_id = ?", Integer.class, "restart-me"));
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test
    void databaseStoreLoadsKeyCreatedByAnotherProvider() throws Exception {
        var database = Files.createTempFile("heidi-signing", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            createSigningKeyTable(jdbc);
            var masterKey = "05".repeat(32);

            try (var first = new SoftwareSigningKeyProvider(
                            new DatabaseSoftwareSigningKeyStore(jdbc, masterKey));
                    var second = new SoftwareSigningKeyProvider(
                            new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                var created = first.createKey("shared-key", "ES256");

                var resolved = second.resolve(created.uri());

                assertEquals(created.publicJwk(), resolved.publicJwk());
                assertEquals(64, second.sign(resolved, new byte[] {1, 2, 3}).length);
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test
    void databaseStoreImportsPkcs12OnlyWhenEmptyAndReusesThePersistedKey() throws Exception {
        var firstPkcs12 = TestCertificates.pkcs12("First issuer");
        var secondPkcs12 = TestCertificates.pkcs12("Second issuer");
        var firstPath = Files.createTempFile("heidi-signing-first", ".p12");
        var secondPath = Files.createTempFile("heidi-signing-second", ".p12");
        var database = Files.createTempFile("heidi-signing", ".db");
        try {
            Files.write(firstPath, firstPkcs12.keystore());
            Files.write(secondPath, secondPkcs12.keystore());
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            createSigningKeyTable(jdbc);
            var masterKey = "04".repeat(32);
            String persistedPublicJwk;

            try (var provider = SoftwareSigningKeyProvider.fromPkcs12(
                    firstPath,
                    firstPkcs12.password().toCharArray(),
                    null,
                    "bootstrap-key",
                    "ES256",
                    new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                var ref = provider.resolve("software://bootstrap-key");
                persistedPublicJwk = ref.publicJwk();
                assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_signing_key", Integer.class));
            }

            try (var provider = SoftwareSigningKeyProvider.fromPkcs12(
                    secondPath,
                    secondPkcs12.password().toCharArray(),
                    null,
                    "bootstrap-key",
                    "ES256",
                    new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                var ref = provider.resolve("software://bootstrap-key");
                assertEquals(persistedPublicJwk, ref.publicJwk());
                assertEquals(64, provider.sign(ref, new byte[] {7, 8, 9}).length);
                provider.deleteKey(ref);
            }

            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_signing_key", Integer.class));
        } finally {
            Files.deleteIfExists(firstPath);
            Files.deleteIfExists(secondPath);
            Files.deleteIfExists(database);
        }
    }

    @Test
    void databaseStorePreservesMultipleAlgorithmsAndPublicKeysAcrossRestart() throws Exception {
        var database = Files.createTempFile("heidi-signing", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            createSigningKeyTable(jdbc);
            var masterKey = "02".repeat(32);
            String es256Uri;
            String es256PublicJwk;
            String edDsaUri;
            String edDsaPublicJwk;
            try (var provider = new SoftwareSigningKeyProvider(
                    new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                var es256 = provider.createKey("restart-es256", "ES256");
                var edDsa = provider.createKey("restart-eddsa", "EdDSA");
                es256Uri = es256.uri();
                es256PublicJwk = es256.publicJwk();
                edDsaUri = edDsa.uri();
                edDsaPublicJwk = edDsa.publicJwk();
            }

            try (var provider = new SoftwareSigningKeyProvider(
                    new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                var es256 = provider.resolve(es256Uri);
                var edDsa = provider.resolve(edDsaUri);

                assertEquals(es256PublicJwk, es256.publicJwk());
                assertEquals("ES256", es256.algorithm());
                assertEquals(64, provider.sign(es256, new byte[] {1, 2, 3}).length);
                assertEquals(edDsaPublicJwk, edDsa.publicJwk());
                assertEquals("EdDSA", edDsa.algorithm());
                assertEquals(64, provider.sign(edDsa, new byte[] {4, 5, 6}).length);
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test
    void databaseStoreRejectsTamperedCiphertextWhenLoading() throws Exception {
        var database = Files.createTempFile("heidi-signing", ".db");
        try {
            var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + database));
            createSigningKeyTable(jdbc);
            var masterKey = "03".repeat(32);
            try (var provider = new SoftwareSigningKeyProvider(
                    new DatabaseSoftwareSigningKeyStore(jdbc, masterKey))) {
                provider.createKey("tamper-me", "ES256");
            }

            var encrypted = jdbc.queryForObject(
                    "SELECT encrypted_private_key FROM t_signing_key WHERE key_id = ?",
                    String.class,
                    "tamper-me");
            assertNotNull(encrypted);
            var ciphertextIndex = encrypted.indexOf(':') + 1;
            var ciphertextCharacter = encrypted.charAt(ciphertextIndex);
            var replacement = ciphertextCharacter == 'A' ? 'B' : 'A';
            jdbc.update(
                    "UPDATE t_signing_key SET encrypted_private_key = ? WHERE key_id = ?",
                    encrypted.substring(0, ciphertextIndex) + replacement
                            + encrypted.substring(ciphertextIndex + 1),
                    "tamper-me");

            var exception = assertThrows(
                    SigningKeyException.class,
                    () -> new SoftwareSigningKeyProvider(
                            new DatabaseSoftwareSigningKeyStore(jdbc, masterKey)));
            assertTrue(exception.getMessage().contains("load"));
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void createSigningKeyTable(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                CREATE TABLE t_signing_key
                (
                    key_id TEXT PRIMARY KEY,
                    algorithm TEXT NOT NULL,
                    encrypted_private_key TEXT NOT NULL,
                    public_key TEXT NOT NULL,
                    encryption_salt TEXT NOT NULL UNIQUE,
                    deletable BOOLEAN NOT NULL DEFAULT TRUE,
                    usages TEXT NOT NULL DEFAULT 'SIGN'
                )
                """);
    }
}
