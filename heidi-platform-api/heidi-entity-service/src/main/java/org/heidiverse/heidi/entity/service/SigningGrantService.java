// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.heidiverse.heidi.shared.signing.SigningGrantWriter;
import org.heidiverse.heidi.shared.signing.SigningKeyScope;
import org.heidiverse.heidi.shared.signing.SigningPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Who may use a key, decided from the role it plays in an identity and written to the signing
 * service that holds it.
 *
 * <pre>
 *   credential key   issuer signs credentials          platform manages
 *   trust key        issuer and verifier state the     platform manages
 *                    identity: metadata, request
 *                    objects, entity statements
 * </pre>
 *
 * <p>The signing service signs bytes and cannot tell a credential from a request object, so this
 * separation only holds where the two roles use different keys.
 */
@Service
public class SigningGrantService {
    public static final String CLIENT_PLATFORM = "platform";
    public static final String CLIENT_ISSUER = "issuer";
    public static final String CLIENT_VERIFIER = "verifier";

    private static final Logger LOGGER = LoggerFactory.getLogger(SigningGrantService.class);

    private final Writers writers;
    private final Map<GrantTarget, Map<String, Set<SigningPurpose>>> pending =
            new ConcurrentHashMap<>();

    @Autowired
    public SigningGrantService(SigningProviderService providerService) {
        this((tenantId, providerId) ->
                providerService.provider(tenantId, providerId) instanceof SigningGrantWriter writer
                        ? writer : null);
    }

    public SigningGrantService(Writers writers) {
        this.writers = writers;
    }

    /** Resolves the provider that holds a tenant's key, or null where it holds no grants. */
    @FunctionalInterface
    public interface Writers {
        SigningGrantWriter of(String tenantId, int providerId);
    }

    public static Map<String, Set<SigningPurpose>> credentialKeyGrants() {
        return Map.of(
                CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT),
                CLIENT_ISSUER, Set.of(SigningPurpose.SIGNING));
    }

    public static Map<String, Set<SigningPurpose>> trustKeyGrants() {
        return Map.of(
                CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT),
                CLIENT_ISSUER, Set.of(SigningPurpose.SIGNING),
                CLIENT_VERIFIER, Set.of(SigningPurpose.SIGNING));
    }

    public static Map<String, Set<SigningPurpose>> operationKeyGrants() {
        return Map.of(
                CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT),
                CLIENT_ISSUER, Set.of(SigningPurpose.OPERATIONS));
    }

    public static Map<String, Set<SigningPurpose>> verifierOperationGrants() {
        return Map.of(
                CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT),
                CLIENT_VERIFIER, Set.of(SigningPurpose.OPERATIONS));
    }

    public static Map<String, Set<SigningPurpose>> platformSigningKeyGrants() {
        return Map.of(CLIENT_PLATFORM,
                Set.of(SigningPurpose.KEY_MANAGEMENT, SigningPurpose.SIGNING));
    }

    public static Map<String, Set<SigningPurpose>> decryptionKeyGrants() {
        return Map.of(
                CLIENT_PLATFORM, Set.of(SigningPurpose.KEY_MANAGEMENT),
                CLIENT_ISSUER, Set.of(SigningPurpose.DECRYPT));
    }

    /**
     * Writes the complete grant list for the key's key. Saving an identity must not fail
     * because a backend holds no grants or is unreachable; the key then stays as it was.
     */
    public void publish(
            String tenantId,
            Integer providerId,
            String keyUri,
            Map<String, Set<SigningPurpose>> grants) {
        if (providerId == null || keyUri == null || keyUri.isBlank()) return;
        publishScope(tenantId, providerId, SigningKeyScope.of(keyUri), grants);
    }

    /** Writes a policy for an exact key URI or operation scope. */
    public void publishScope(
            String tenantId,
            Integer providerId,
            String scope,
            Map<String, Set<SigningPurpose>> grants) {
        if (providerId == null || scope == null || scope.isBlank()) return;
        var target = new GrantTarget(tenantId, providerId, scope);
        var desired = copy(grants);

        synchronized (pending) {
            if (tryPublish(target, desired)) {
                pending.remove(target);
                return;
            }
            pending.put(target, desired);
        }
    }

    /** Whether a provider policy still needs to be reconciled. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Provider-confirmed state for one desired policy. A null policy means unavailable. */
    public GrantStatus status(String tenantId, int providerId, String scope) {
        var target = new GrantTarget(tenantId, providerId, scope);
        try {
            var writer = writers.of(tenantId, providerId);
            var confirmed = writer == null ? null : writer.grants(scope).map(SigningGrantService::copy).orElse(null);
            return new GrantStatus(confirmed, pending.containsKey(target));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read signing grants for {} on provider {} of tenant {}",
                    scope, providerId, tenantId, exception);
            return new GrantStatus(null, pending.containsKey(target));
        }
    }

    public record GrantStatus(Map<String, Set<SigningPurpose>> confirmed, boolean pending) {}

    /** Returns persisted scopes so callers can close removed operation bindings. */
    public Set<String> scopes(String tenantId, int providerId) {
        SigningGrantWriter writer;
        try {
            writer = writers.of(tenantId, providerId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not reach provider {} of tenant {}", providerId, tenantId, exception);
            return Set.of();
        }
        if (writer == null) return Set.of();
        try {
            return writer.grantScopes();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not list grant scopes for provider {} of tenant {}",
                    providerId, tenantId, exception);
            return Set.of();
        }
    }

    @Scheduled(fixedDelayString = "${heidi.platform.signing-grants.retry-interval-ms:30000}")
    void retryPending() {
        synchronized (pending) {
            pending.forEach((target, grants) -> {
                if (tryPublish(target, grants)) pending.remove(target, grants);
            });
        }
    }

    private boolean tryPublish(GrantTarget target, Map<String, Set<SigningPurpose>> grants) {
        SigningGrantWriter writer;
        try {
            writer = writers.of(target.tenantId(), target.providerId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not reach provider {} of tenant {}", target.providerId(), target.tenantId(), exception);
            return false;
        }
        if (writer == null) return true;

        try {
            // Periodic expiry checks recompute every policy. Avoid replacing unchanged provider state.
            if (samePolicy(writer, target.scope(), grants)) return true;

            writer.replaceGrants(target.scope(), grants);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not write signing grants for {} on provider {} of tenant {}",
                    target.scope(), target.providerId(), target.tenantId(), exception);
            return false;
        }
    }

    private boolean samePolicy(
            SigningGrantWriter writer, String scope, Map<String, Set<SigningPurpose>> desired) {
        try {
            return writer.grants(scope).filter(desired::equals).isPresent();
        } catch (RuntimeException exception) {
            // A new scope may not be readable until its first administrator policy is written.
            LOGGER.debug("Could not compare signing grants for {}; writing the policy", scope, exception);
            return false;
        }
    }

    private static Map<String, Set<SigningPurpose>> copy(
            Map<String, Set<SigningPurpose>> grants) {
        return grants.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    private record GrantTarget(String tenantId, Integer providerId, String scope) {}
}
