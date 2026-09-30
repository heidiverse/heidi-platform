// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

import java.util.List;

/**
 * A backend that holds signing keys and signs with them.
 *
 * <p>This is the whole required surface. Creating, importing and deleting keys are separate
 * interfaces, because a backend that cannot do them should not offer the method at all — a
 * qualified signature service issues its own keys and will never import one, and a cloud KMS
 * commonly refuses deletion. See {@link SigningKeyCapabilities} for the projection a user
 * interface needs.
 *
 * <h2>What sign() receives</h2>
 *
 * <p>The <em>message</em>, never a digest. Callers must not pre-hash: the provider applies whatever
 * the algorithm requires. This matters for {@code EdDSA} — PureEd25519 signs the message itself and
 * cannot be reconstructed from a hash — so a backend whose remote API only accepts digests has to
 * declare it does not support {@code EdDSA} rather than quietly signing the wrong thing.
 *
 * <h2>What sign() returns</h2>
 *
 * <p>The signature in its JOSE encoding for the algorithm, as RFC 7518 defines it: fixed-width
 * {@code R || S} for {@code ES*}, PKCS#1 v1.5 for {@code RS*}, PSS for {@code PS*}, the raw
 * signature for {@code EdDSA} and the {@code ML-DSA-*} family. A caller that needs X.509 or CMS
 * re-encodes — for ECDSA that means wrapping {@code R || S} into an ECDSA-Sig-Value DER sequence.
 *
 * <p>One encoding is fixed here rather than left per provider so that two providers signing with
 * the same algorithm produce interchangeable output.
 */
public interface SigningKeyProvider {

    /** URI scheme this provider answers to, without separator, e.g. {@code local}. */
    String scheme();

    /**
     * Algorithms this provider can sign with, as JOSE names.
     *
     * <p>Independent of what the platform allows an issuer to configure. What may actually be
     * offered is the intersection of the two, which is why this is asked rather than assumed.
     */
    List<String> supportedAlgorithms();

    /**
     * Look up a key the provider already holds.
     *
     * @throws SigningKeyException if the URI is unknown to this provider
     */
    SigningKeyRef resolve(String keyUri);

    /**
     * Algorithms this provider can sign a caller-supplied digest with.
     *
     * <p>A subset of {@link #supportedAlgorithms()}, and legitimately empty. Hash-then-sign
     * algorithms generally qualify; the pure schemes never do, because {@code EdDSA} is PureEd25519
     * and {@code ML-DSA-*} signs the message directly, so neither can be reconstructed from a hash.
     */
    default List<String> digestSigningAlgorithms() {
        return List.of();
    }

    /**
     * Sign {@code message} with the referenced key.
     *
     * @throws SigningKeyException if the key is unusable or the backend refuses
     */
    byte[] sign(SigningKeyRef ref, byte[] message);

    /**
     * Sign a digest the caller already computed.
     *
     * <p>Worth choosing over {@link #sign} in two situations: when the provider should not learn
     * what is being signed — a signing service someone else operates learns nothing about a
     * credential from its hash — and when the payload is large enough that shipping it is wasteful.
     *
     * <p>{@code digestAlgorithm} must be the hash the signature algorithm prescribes, so a
     * mismatch is refused rather than turned into a signature over the wrong construction.
     *
     * @throws SigningKeyException if the algorithm is not in {@link #digestSigningAlgorithms()},
     *     the digest algorithm does not match, or the backend refuses
     */
    default byte[] signDigest(SigningKeyRef ref, byte[] digest, String digestAlgorithm) {
        throw new SigningKeyException(
                "Provider '" + scheme() + "' cannot sign a pre-computed digest");
    }

    /** Cheap liveness probe. Implementations report failure rather than throwing. */
    ProviderHealth health();
}
