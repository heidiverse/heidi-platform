// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class SigningContractTest {

    /** A backend that only signs: no creation, no import, no deletion. */
    private static class SignOnlyProvider implements SigningKeyProvider {
        @Override public String scheme() { return "qes"; }
        @Override public List<String> supportedAlgorithms() { return List.of("ES256", "RS256"); }
        @Override public SigningKeyRef resolve(String keyUri) {
            return new SigningKeyRef(keyUri, "{}", "ES256");
        }
        @Override public byte[] sign(SigningKeyRef ref, byte[] message) { return new byte[64]; }
        @Override public List<String> digestSigningAlgorithms() { return List.of("ES256", "RS256"); }
        @Override public byte[] signDigest(SigningKeyRef r, byte[] d, String alg) { return new byte[64]; }
        @Override public ProviderHealth health() { return ProviderHealth.up(3); }
    }

    /** A software backend that can do everything. */
    private static final class FullProvider extends SignOnlyProvider
            implements SigningKeyCreator, SigningKeyImporter, SigningKeyDeleter {
        @Override public String scheme() { return "software"; }
        @Override public List<String> supportedAlgorithms() { return List.of("ES256", "EdDSA"); }
        @Override public List<String> digestSigningAlgorithms() { return List.of("ES256"); }
        @Override public SigningKeyRef createKey(String keyId, String algorithm) {
            return new SigningKeyRef("software://" + keyId, "{}", algorithm);
        }
        @Override public SigningKeyRef importKey(String keyId, String privateJwk, String algorithm) {
            return createKey(keyId, algorithm);
        }
        @Override public void deleteKey(SigningKeyRef ref) {}
    }

    @Test
    void schemeSelectsTheProvider() {
        var ref = new SigningKeyRef("vault://transit/keys/issuer-1", "{}", "ES256");
        assertEquals("vault", ref.scheme());
        // Everything after the scheme belongs to the provider and stays unparsed.
        assertEquals("vault://transit/keys/issuer-1", ref.uri());
    }

    @Test
    void rejectsReferencesThatCannotRouteAnOperation() {
        assertThrows(IllegalArgumentException.class,
                () -> new SigningKeyRef(" ", "{}", "ES256"));
        assertThrows(IllegalArgumentException.class,
                () -> new SigningKeyRef("no-scheme", "{}", "ES256"));
        assertThrows(IllegalArgumentException.class,
                () -> new SigningKeyRef("software://key", " ", "ES256"));
        assertThrows(IllegalArgumentException.class,
                () -> new SigningKeyRef("software://key", "{}", ""));
    }

    @Test
    void capabilitiesFollowTheImplementedInterfaces() {
        var limited = SigningKeyCapabilities.of(new SignOnlyProvider());
        assertEquals("qes", limited.scheme());
        assertFalse(limited.canCreate());
        assertFalse(limited.canImport());
        assertFalse(limited.canDelete());

        var full = SigningKeyCapabilities.of(new FullProvider());
        assertTrue(full.canCreate());
        assertTrue(full.canImport());
        assertTrue(full.canDelete());
    }

    @Test
    void digestSigningIsOptionalAndRefusedByDefault() {
        // A provider that says nothing about digests must not silently accept one.
        var messageOnly = new SigningKeyProvider() {
            @Override public String scheme() { return "pure"; }
            @Override public List<String> supportedAlgorithms() { return List.of("EdDSA"); }
            @Override public SigningKeyRef resolve(String uri) {
                return new SigningKeyRef(uri, "{}", "EdDSA");
            }
            @Override public byte[] sign(SigningKeyRef ref, byte[] message) { return new byte[64]; }
            @Override public ProviderHealth health() { return ProviderHealth.up(1); }
        };
        assertTrue(messageOnly.digestSigningAlgorithms().isEmpty());
        assertThrows(
                SigningKeyException.class,
                () -> messageOnly.signDigest(
                        messageOnly.resolve("pure://k"), new byte[32], "SHA-256"));
    }

    @Test
    void digestSigningIsNarrowerThanSigning() {
        // PureEd25519 cannot be reconstructed from a hash, so it is signable but not digest-signable.
        var full = new FullProvider();
        assertTrue(full.supportedAlgorithms().contains("EdDSA"));
        assertFalse(full.digestSigningAlgorithms().contains("EdDSA"));
        assertTrue(SigningKeyCapabilities.of(full).digestSigningAlgorithms().contains("ES256"));
    }

    @Test
    void offerableAlgorithmsAreTheIntersectionWithWhatThePlatformAllows() {
        var platform = List.of("ES256", "EdDSA", "ML-DSA-65");
        // The provider cannot do ML-DSA, so it must not be offered even though the platform allows it.
        assertEquals(
                List.of("ES256", "EdDSA"),
                SigningKeyCapabilities.of(new FullProvider()).offerableAlgorithms(platform));
        // And an algorithm the provider has but the platform does not advertise stays out too.
        assertEquals(
                List.of("ES256"),
                SigningKeyCapabilities.of(new SignOnlyProvider()).offerableAlgorithms(platform));
    }
}
