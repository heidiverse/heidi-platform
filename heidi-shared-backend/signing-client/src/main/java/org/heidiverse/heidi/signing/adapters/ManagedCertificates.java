// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.signing.adapters;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import uniffi.heidi_pki_x509.*;

/** Managed VC certificates; kapun owns parsing and certificate cryptography. */
public final class ManagedCertificates {
    private ManagedCertificates() {}

    @FunctionalInterface
    public interface Call<T> { T run(PkiX509 x509) throws Exception; }

    public static <T> T use(Call<T> call) {
        try (var x509 = new PkiX509()) {
            return call.run(x509);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid managed certificate: " + error.getMessage(), error);
        }
    }

    public static byte[] decode(String encoded) {
        if (!encoded.stripLeading().startsWith("-----BEGIN")) {
            return Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
        }
        return use(x509 -> {
            var chain = x509.parsePemChain(encoded);
            if (chain.size() != 1) throw new IllegalArgumentException("One certificate required");
            return chain.getFirst();
        });
    }

    public static CertificateInfo info(String encoded) {
        return use(x509 -> x509.inspect(decode(encoded)));
    }

    public static byte[] subject(String name) {
        return use(x509 -> x509.encodeName(name, NameOrder.INPUT));
    }

    public static boolean selfSigned(String encoded) {
        return use(x509 -> x509.selfSigned(decode(encoded)));
    }

    public static void valid(String encoded) {
        use(x509 -> { x509.checkValidity(decode(encoded), Instant.now().getEpochSecond()); return null; });
    }

    public static List<CertificateInfo> validate(List<String> chain, ChainKind kind,
            ChainEnd end, IssuerUsage usage, byte[] expectedSpki) {
        var der = chain.stream().map(ManagedCertificates::decode).toList();
        return use(x509 -> x509.validateChain(der, new ChainPolicy(kind, end, usage,
                Instant.now().getEpochSecond(), List.of(), expectedSpki)));
    }

    public static SignatureAlgorithm algorithm(String algorithm) {
        return switch (algorithm) {
            case "EdDSA" -> SignatureAlgorithm.ED25519;
            default -> SignatureAlgorithm.valueOf(algorithm);
        };
    }
}
