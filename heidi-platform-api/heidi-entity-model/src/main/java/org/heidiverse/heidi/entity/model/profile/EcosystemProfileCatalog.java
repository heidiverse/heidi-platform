// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import java.util.List;
import java.util.Optional;

import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierClientIdScheme;

import static org.heidiverse.heidi.entity.model.profile.EcosystemProfileFamily.*;
import static org.heidiverse.heidi.entity.model.profile.EcosystemProfileId.*;
import static org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole.*;

/** Read-only catalogue of profile manifests supported by this platform release. */
public final class EcosystemProfileCatalog {
    private static final List<EcosystemProfile> PROFILES = List.of(
            issuance(EUDI_ISSUANCE_2026_1, EUDI_WALLET, "EUDI Profile 1.0", IssuerTrustSystem.EUDI,
                    List.of("ES256"), List.of("ES256", "ES384", "EdDSA"), List.of("ES256"),
                    encryption(
                            algorithms(List.of("ECDH-ES"), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of("A256GCM"), List.of("A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            issuance(SWISS_ISSUANCE_2026_1, SWISS_SWIYU, "Swiss Profile 1.0", IssuerTrustSystem.Switzerland,
                    List.of("ES256"), List.of("ES256"), List.of("ES256"),
                    encryption(
                            algorithms(List.of("ECDH-ES"), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of("A256GCM"), List.of("A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            issuance(OIDF_ISSUANCE_2026_1, OIDF, "OpenID Federation", IssuerTrustSystem.OIDF,
                    List.of(), List.of("ES256", "ES384", "EdDSA", "BBS"), List.of("ES256"),
                    encryption(
                            algorithms(List.of(), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of(), List.of("A128GCM", "A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            issuance(CUSTOM_ISSUANCE_2026_1, CUSTOM, "Custom / manual", IssuerTrustSystem.Custom,
                    List.of(), List.of("ES256", "ES384", "EdDSA", "BBS"), List.of("ES256"),
                    encryption(
                            algorithms(List.of(), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of(), List.of("A128GCM", "A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            presentation(EUDI_PRESENTATION_2026_1, EUDI_WALLET, "EUDI Profile 1.0", IssuerTrustSystem.EUDI,
                    VerifierClientIdScheme.X509_HASH, "direct_post.jwt",
                    List.of("ES256"), List.of("ES256", "ES384", "EdDSA"), List.of("ES256"),
                    encryption(
                            algorithms(List.of("ECDH-ES"), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of("A128GCM", "A256GCM"),
                                    List.of("A128GCM", "A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            presentation(SWISS_PRESENTATION_2026_1, SWISS_SWIYU, "Swiss Profile 1.0", IssuerTrustSystem.Switzerland,
                    VerifierClientIdScheme.DECENTRALIZED_IDENTIFIER, "direct_post.jwt",
                    List.of("ES256"), List.of("ES256"), List.of("ES256"),
                    encryption(
                            algorithms(List.of("ECDH-ES"), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of("A256GCM"), List.of("A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            presentation(OIDF_PRESENTATION_2026_1, OIDF, "OpenID Federation", IssuerTrustSystem.OIDF,
                    VerifierClientIdScheme.OPENID_FEDERATION, "direct_post.jwt",
                    List.of(), List.of("ES256", "ES384", "EdDSA"), List.of("ES256"),
                    encryption(
                            algorithms(List.of(), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of(), List.of("A128GCM", "A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))),
            presentation(CUSTOM_PRESENTATION_2026_1, CUSTOM, "Custom / manual", IssuerTrustSystem.Custom,
                    VerifierClientIdScheme.X509_SAN_DNS, "direct_post",
                    List.of(), List.of("ES256", "ES384", "EdDSA"), List.of("ES256"),
                    encryption(
                            algorithms(List.of(), List.of("ECDH-ES"), List.of()),
                            algorithms(List.of(), List.of("A128GCM", "A256GCM"), List.of("A256GCM")),
                            algorithms(List.of(), List.of(), List.of()))));

    private EcosystemProfileCatalog() {}

    public static List<EcosystemProfile> all() {
        return PROFILES;
    }

    public static Optional<EcosystemProfile> find(String id) {
        return PROFILES.stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    public static Optional<EcosystemProfile> find(String id, EcosystemProfileRole role) {
        return find(id).filter(profile -> profile.role() == role);
    }

    public static EcosystemProfile require(String id, EcosystemProfileRole role) {
        return find(id, role).orElseThrow(() -> new IllegalArgumentException(
                "Unknown " + role.name().toLowerCase() + " ecosystem profile: " + id));
    }

    private static EcosystemProfile issuance(
            String id, EcosystemProfileFamily family, String displayName,
            IssuerTrustSystem trustSystem, List<String> requiredSigning,
            List<String> supportedSigning, List<String> preferredSigning,
            EncryptionAlgorithmConstraints encryption) {
        return new EcosystemProfile(id, ISSUANCE, family, version(id, ISSUANCE), displayName,
                new EcosystemProfilePolicy(trustSystem, null, "direct_post",
                        new AlgorithmConstraints(requiredSigning, supportedSigning, preferredSigning),
                        encryption));
    }

    private static EcosystemProfile presentation(
            String id, EcosystemProfileFamily family, String displayName,
            IssuerTrustSystem trustSystem, VerifierClientIdScheme clientIdScheme,
            String responseMode, List<String> requiredSigning, List<String> supportedSigning,
            List<String> preferredSigning, EncryptionAlgorithmConstraints encryption) {
        return new EcosystemProfile(id, PRESENTATION, family, version(id, PRESENTATION), displayName,
                new EcosystemProfilePolicy(trustSystem, clientIdScheme, responseMode,
                        new AlgorithmConstraints(requiredSigning, supportedSigning, preferredSigning),
                        encryption));
    }

    private static AlgorithmConstraints algorithms(
            List<String> required, List<String> supported, List<String> preferred) {
        return new AlgorithmConstraints(required, supported, preferred);
    }

    private static EncryptionAlgorithmConstraints encryption(
            AlgorithmConstraints alg, AlgorithmConstraints enc, AlgorithmConstraints zip) {
        return new EncryptionAlgorithmConstraints(alg, enc, zip);
    }

    private static String version(String id, EcosystemProfileRole role) {
        if (SWISS_ISSUANCE_2026_1.equals(id)) return "swiss-profile-issuance:1.0.0";
        if (SWISS_PRESENTATION_2026_1.equals(id)) return "swiss-profile-verification:1.0.0";
        return "2026.1";
    }
}
