// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import com.nimbusds.jwt.JWTClaimNames;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public class SdJwtUtil {
    /** SD-JWT selectively disclosable claim digest values */
    public static final String SD_CLAIM = "_sd";

    /** SD-JWT selectively disclosable claim digest algorithm */
    public static final String SD_ALG_CLAIM = "_sd_alg";

    /** SD-JWT selectively disclosable array element digest value */
    public static final String ARRAY_ELEM_CLAIM = "...";

    /** SD-JWT claim name - encodes public key used to sign KB-JWT */
    public static final String CONFIRMATION_KEY = "cnf";

    /** SD-JWT verifiable credential type claim */
    public static final String VERIFIABLE_CREDENTIAL_TYPE = "vct";

    /** KB-JWT nonce claim */
    public static final String NONCE = "nonce";

    /** KB-JWT transaction data claim */
    public static final String TRANSACTION_DATA = "transaction_data_hashes";

    /**
     * KB-JWT claim name - its value contains the digest value of the SD-JWT and the selected
     * disclosures
     */
    public static final String SD_HASH = "sd_hash";

    /** Supported signature algorithms */
    public static final List<String> DEFAULT_SUPPORTED_SIG_ALGORITHMS =
            List.of("ES256", "ES384", "ES512", "EdDSA");

    /** KB-JWT required claim names */
    public static final Set<String> KB_JWT_CLAIMS = Set.of(JWTClaimNames.ISSUED_AT, SD_HASH);

    /** JWT header 'typ' claim value for KB-JWTs */
    public static final String KB_JWT_TYP = "kb+jwt";

    /** Default value for KB-JWT max validity period */
    public static final Duration KB_JWT_MAX_VALIDITY_PERIOD = Duration.ofMinutes(10);

    /** Claim names reserved to selective disclosures */
    private static final Set<String> RESERVED_CLAIM_NAMES =
            Set.of(SD_CLAIM, SD_ALG_CLAIM, ARRAY_ELEM_CLAIM);

    /** Default hash algorithm if none specified */
    private static final String STANDARD_HASH_ALGORITHM = "SHA-256";

    /** Supported hash algorithms */
    private static final List<String> SUPPORTED_HASH_ALGORITHMS =
            List.of(
                    STANDARD_HASH_ALGORITHM,
                    "SHA-384",
                    "SHA-512",
                    "SHA3-256",
                    "SHA3-384",
                    "SHA3-512");

    /** As used in verifiable credentials encoded as SD-JWTs */
    public static final String DEFAULT_VC_TYP = "dc+sd-jwt";

    /** pre SD-JWT VC pre draft 06 used "vc+sd-jwt" */
    public static final String LEGACY_VC_TYP = "vc+sd-jwt";

    private SdJwtUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isSupportedHashAlgorithms(final String algorithm) {
        return SUPPORTED_HASH_ALGORITHMS.contains(algorithm);
    }

    public static String getStandardHashAlgorithm() {
        return STANDARD_HASH_ALGORITHM;
    }

    public static String stripNewlines(String s) {
        return s.replaceAll("[\\r\\n]+", "");
    }

    public static String computeDigest(MessageDigest hash, String encoded) {
        // Ensure only the encoded string is digested
        hash.reset();

        // Calculate the digest over the base64url-encoded string.
        hash.update(encoded.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash.digest());
    }

    public static Set<String> getDefaultReservedClaimNames() {
        return RESERVED_CLAIM_NAMES;
    }
}
