// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.time.Instant;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.signing.server.RegisteredKeyAuthenticationStore;
import org.heidiverse.heidi.signing.software.SoftwareSigningKeyEncryption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

/** PostgreSQL store for registered-key credentials and replay protection. */
public final class JdbcRegisteredKeyAuthenticationStore
        implements RegisteredKeyAuthenticationStore {
    private final JdbcTemplate jdbc;
    private final SoftwareSigningKeyEncryption encryption;

    public JdbcRegisteredKeyAuthenticationStore(JdbcTemplate jdbc, String masterKeyHex) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC template must not be null");
        this.encryption = new SoftwareSigningKeyEncryption(masterKeyHex);
    }

    @Override
    public void create(String registrationId, byte[] psk, String client) {
        try {
            jdbc.update(
                    "INSERT INTO t_signing_auth_registration "
                            + "(registration_id, encrypted_psk, client_name) VALUES (?, ?, ?)",
                    registrationId,
                    encrypt(registrationId, psk),
                    client);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist signing registration", exception);
        }
    }

    @Override
    public Optional<RegistrationRecord> find(String registrationId) {
        try {
            return jdbc.query(
                            "SELECT encrypted_psk, public_key, client_name "
                                    + "FROM t_signing_auth_registration WHERE registration_id = ?",
                            result -> result.next()
                                    ? Optional.of(new RegistrationRecord(
                                            registrationId,
                                            decrypt(registrationId, result.getString("encrypted_psk")),
                                            decodePublicKey(result.getString("public_key")),
                                            result.getString("client_name")))
                                    : Optional.empty(),
                            registrationId);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not load signing registration", exception);
        }
    }

    @Override
    public Optional<RegistrationRecord> findByClient(String client, byte[] publicKey) {
        try {
            return jdbc.query(
                            "SELECT registration_id, encrypted_psk "
                                    + "FROM t_signing_auth_registration "
                                    + "WHERE client_name = ? AND public_key = ?",
                            result -> {
                                if (!result.next()) return Optional.empty();
                                var id = result.getString("registration_id");
                                return Optional.of(new RegistrationRecord(
                                        id,
                                        decrypt(id, result.getString("encrypted_psk")),
                                        publicKey,
                                        client));
                            },
                            client,
                            Base64.getEncoder().encodeToString(publicKey));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not load the client's signing registration", exception);
        }
    }

    @Override
    public boolean hasCompletedRegistration(String client) {
        try {
            ResultSetExtractor<Boolean> exists = result -> result.next();
            return Boolean.TRUE.equals(jdbc.query(
                    "SELECT 1 FROM t_signing_auth_registration "
                            + "WHERE client_name = ? AND public_key IS NOT NULL LIMIT 1",
                    exists,
                    client));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not read the client's registration state", exception);
        }
    }

    @Override
    public void recordUse(String registrationId, Instant usedAt) {
        try {
            jdbc.update(
                    "UPDATE t_signing_auth_registration SET last_used_at = ? WHERE registration_id = ?",
                    Timestamp.from(usedAt), registrationId);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist signing registration use", exception);
        }
    }

    @Override
    public Optional<Instant> lastUse(String client) {
        try {
            return jdbc.query(
                    "SELECT MAX(last_used_at) AS last_used_at FROM t_signing_auth_registration "
                            + "WHERE client_name = ? AND public_key IS NOT NULL",
                    result -> {
                        if (!result.next()) {
                            return Optional.empty();
                        }
                        return readInstant(result);
                    },
                    client);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not read signing registration use", exception);
        }
    }

    @Override
    public boolean setPublicKeyIfAbsent(String registrationId, byte[] publicKey) {
        try {
            return jdbc.update(
                            "UPDATE t_signing_auth_registration SET public_key = ? "
                                    + "WHERE registration_id = ? AND public_key IS NULL",
                            Base64.getEncoder().encodeToString(publicKey),
                            registrationId)
                    == 1;
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist signing registration key", exception);
        }
    }

    @Override
    public boolean recordSignatureIfAbsent(String signature, long seenAt) {
        try {
            return jdbc.update(
                            "INSERT INTO t_signing_auth_replay(signature, seen_at) VALUES (?, ?) "
                                    + "ON CONFLICT (signature) DO NOTHING",
                            signature,
                            seenAt)
                    == 1;
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not persist signing replay state", exception);
        }
    }

    @Override
    public void removeSignature(String signature, long seenAt) {
        try {
            jdbc.update(
                    "DELETE FROM t_signing_auth_replay WHERE signature = ? AND seen_at = ?",
                    signature,
                    seenAt);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not remove signing replay state", exception);
        }
    }

    @Override
    public void purgeSignaturesBefore(long cutoff) {
        try {
            jdbc.update("DELETE FROM t_signing_auth_replay WHERE seen_at < ?", cutoff);
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not purge signing replay state", exception);
        }
    }

    @Override
    public void purgeUncompletedBefore(Instant cutoff) {
        try {
            jdbc.update(
                    "DELETE FROM t_signing_auth_registration "
                            + "WHERE public_key IS NULL AND created_at < ?",
                    Timestamp.from(cutoff));
        } catch (RuntimeException exception) {
            throw new SigningKeyException("Could not purge incomplete signing registrations", exception);
        }
    }

    private String encrypt(String registrationId, byte[] psk) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                encryption.encrypt(psk, aad(registrationId)));
    }

    private byte[] decrypt(String registrationId, String encryptedPsk) {
        try {
            return encryption.decrypt(
                    Base64.getUrlDecoder().decode(encryptedPsk), aad(registrationId));
        } catch (IllegalArgumentException exception) {
            throw new SigningKeyException("Could not decrypt signing registration", exception);
        }
    }

    private static byte[] decodePublicKey(String value) {
        return value == null ? null : Base64.getDecoder().decode(value);
    }

    private static Optional<Instant> readInstant(ResultSet result) throws java.sql.SQLException {
        var value = result.getObject("last_used_at");
        if (value == null) return Optional.empty();
        if (value instanceof Timestamp timestamp) return Optional.of(timestamp.toInstant());
        if (value instanceof Number number) return Optional.of(Instant.ofEpochMilli(number.longValue()));
        var text = value.toString();
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(text)));
        } catch (NumberFormatException ignored) {
            // PostgreSQL returns a timestamp string while SQLite may expose the epoch value.
        }
        try {
            return Optional.of(Instant.parse(text));
        } catch (DateTimeParseException ignored) {
            return Optional.of(Timestamp.valueOf(text.replace('T', ' ').replace("Z", "")).toInstant());
        }
    }

    private static byte[] aad(String registrationId) {
        return ("heidi-signing-registration:" + registrationId)
                .getBytes(StandardCharsets.UTF_8);
    }
}
