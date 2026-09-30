// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import java.util.List;

/**
 * Supplies the certificate material a local development stack needs to close the
 * issue-then-verify loop without an external certificate authority.
 *
 * <p>A deployment obtains its issuing certificate from a CA its trust regime puts on a Trusted
 * List, so nothing in production implements this. The one implementation lives behind the {@code
 * local} profile and mints a throw-away CA on the developer's machine; keeping it an interface
 * keeps key generation out of {@link IssuerService} and off every other code path.
 */
public interface LocalDevelopmentTrustSeed {

    /** Base64 DER of the development root certificate, to be trusted when verifying. */
    String rootCertificate();

    /**
     * The issuer identifier every leaf binds in its subject alternative name.
     *
     * <p>A key added after start-up has no seeding call to carry the identifier along, so the
     * seed itself holds the one the local stack issues under.
     */
    String issuerIdentifier();

    /**
     * A leaf-to-root chain, base64 DER, over the given SubjectPublicKeyInfo.
     *
     * @param subjectName the common name to issue the leaf under
     * @param issuer the issuer identifier to bind in the subject alternative name
     * @param subjectPublicKeyInfo DER-encoded SubjectPublicKeyInfo of the identity key
     */
    List<String> certificateChain(
            String subjectName, String issuer, byte[] subjectPublicKeyInfo)
            throws Exception;
}
