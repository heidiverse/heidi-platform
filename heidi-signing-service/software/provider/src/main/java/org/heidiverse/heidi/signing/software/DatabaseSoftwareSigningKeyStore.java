// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.software;

import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL-compatible store that encrypts private key bytes before they reach the database. */
public final class DatabaseSoftwareSigningKeyStore implements SoftwareSigningKeyStore {
    private final JdbcTemplate jdbc;
    private final SoftwareSigningKeyEncryption encryption;

    public DatabaseSoftwareSigningKeyStore(JdbcTemplate jdbc, String encryptionMasterKey) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template must not be null");
        this.encryption = new SoftwareSigningKeyEncryption(encryptionMasterKey);
    }

    @Override
    public List<StoredSoftwareSigningKey> load() {
        try {
            return jdbc.query(
                    """
                    SELECT key_id, algorithm, encrypted_private_key, public_key, encryption_salt, deletable, usages
                    FROM t_signing_key
                    ORDER BY key_id
                    """,
                    (result, row) -> storedKey(
                            result.getString("key_id"),
                            result.getString("algorithm"),
                            result.getString("encrypted_private_key"),
                            result.getString("public_key"),
                            result.getString("encryption_salt"),
                            result.getBoolean("deletable"),
                            result.getString("usages")));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not load software signing keys from the database", exception);
        }
    }

    @Override
    public Optional<StoredSoftwareSigningKey> find(String keyId) {
        try {
            return jdbc.query(
                            """
                            SELECT key_id, algorithm, encrypted_private_key, public_key, encryption_salt, deletable, usages
                            FROM t_signing_key
                            WHERE key_id = ?
                            """,
                            (result, row) -> storedKey(
                                    result.getString("key_id"),
                                    result.getString("algorithm"),
                                    result.getString("encrypted_private_key"),
                                    result.getString("public_key"),
                                    result.getString("encryption_salt"),
                                    result.getBoolean("deletable"),
                                    result.getString("usages")),
                            keyId)
                    .stream()
                    .findFirst();
        } catch (RuntimeException exception) {
            throw new SigningKeyException(
                    "Could not load software signing key: software://" + keyId, exception);
        }
    }

    @Override
    public void save(StoredSoftwareSigningKey key) {
        var saltBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(saltBytes);
        var salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
        var privateKey = key.privateKey();
        try {
            var encrypted = encryption.encrypt(privateKey, salt, key.keyId(), key.algorithm());
            jdbc.update(
                    """
                    INSERT INTO t_signing_key
                        (key_id, algorithm, encrypted_private_key, public_key, encryption_salt, deletable, usages)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    key.keyId(),
                    key.algorithm(),
                    encrypted,
                    Base64.getEncoder().encodeToString(key.publicKey()),
                    salt,
                    key.deletable(),
                    encodeUsages(key.usages()));
        } catch (RuntimeException exception) {
            throw new SigningKeyException(
                    "Could not persist software signing key: software://" + key.keyId(), exception);
        } finally {
            Arrays.fill(privateKey, (byte) 0);
            Arrays.fill(saltBytes, (byte) 0);
        }
    }

    @Override
    public void delete(String keyId) {
        try {
            var deleted = jdbc.update("DELETE FROM t_signing_key WHERE key_id = ?", keyId);
            if (deleted != 1) {
                throw new SigningKeyException("Unknown software signing key: software://" + keyId);
            }
        } catch (SigningKeyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SigningKeyException(
                    "Could not delete software signing key: software://" + keyId, exception);
        }
    }

    private StoredSoftwareSigningKey storedKey(
            String keyId,
            String algorithm,
            String encryptedPrivateKey,
            String publicKey,
            String salt,
            boolean deletable,
            String usages) {
        var privateKey = encryption.decrypt(encryptedPrivateKey, salt, keyId, algorithm);
        try {
            return new StoredSoftwareSigningKey(
                    keyId,
                    algorithm,
                    privateKey,
                    Base64.getDecoder().decode(publicKey),
                    deletable,
                    decodeUsages(usages));
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private static String encodeUsages(Set<SigningKeyUsage> usages) {
        return usages.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private static Set<SigningKeyUsage> decodeUsages(String value) {
        if (value == null || value.isBlank()) return Set.of(SigningKeyUsage.SIGN);
        try {
            return Set.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .map(SigningKeyUsage::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new SigningKeyException("Stored signing key has invalid usages: " + value, exception);
        }
    }
}
