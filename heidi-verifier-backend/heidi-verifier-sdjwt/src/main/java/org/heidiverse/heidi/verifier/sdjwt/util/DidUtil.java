// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import com.nimbusds.jose.jwk.JWK;

import org.kapunsdk.trust.did.DidResolver;
import org.kapunsdk.trust.did.models.DidLogEntry;
import org.kapunsdk.util.extensions.ValueExtensionKt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import uniffi.kapun_crypto_rust.DidVerificationDocument;
import uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt;
import uniffi.kapun_crypto_rust.VerificationMethod;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;

public class DidUtil {

    private static final String DID_TDW_PREFIX = "did:tdw:";
    private static final String DID_WEBVH_PREFIX = "did:webvh:";
    private static final RestTemplate restTemplate = new RestTemplate();

    private DidUtil() {
        // Utility class, do not instantiate
    }

    public static boolean isSupportedDid(String did) {
        return did != null && (did.startsWith(DID_TDW_PREFIX) || did.startsWith(DID_WEBVH_PREFIX));
    }

    /**
     * Resolves a JWK from a did:webvh or did:tdw identifier.
     *
     * @param didKeyId The full DID key ID (e.g., did:tdw:...#assert-key-...)
     * @return The matching public JWK
     * @throws IOException If HTTP or parsing fails
     * @throws ParseException If the DID document is malformed or JWK is missing
     */
    public static JWK resolveJwkFromDidKey(String didKeyId) throws IOException, ParseException {
        String did = didKeyId.split("#")[0];
        String httpsUrl = transformDidToHttpsUrl(did);
        String didDocumentRaw = fetchDidJsonl(httpsUrl);

        DidVerificationDocument didDocument = resolveAndVerify(didDocumentRaw);
        for (VerificationMethod verificationMethod : didDocument.getVerificationMethod()) {
            if (didKeyId.equals(verificationMethod.getId())
                    && verificationMethod.getPublicKeyJwk() != null) {
                Object publicKeyJwk =
                        ValueExtensionKt.toPlainObject(verificationMethod.getPublicKeyJwk());
                if (publicKeyJwk instanceof Map<?, ?> rawMap
                        && rawMap.keySet().stream().allMatch(String.class::isInstance)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jwk = (Map<String, Object>) rawMap;
                    return JWK.parse(jwk);
                }
            }
        }

        throw new ParseException(
                "No matching verification method found for key ID: " + didKeyId, 0);
    }

    private static DidVerificationDocument resolveAndVerify(String didJsonl) throws IOException {
        try {
            List<String> entries = didJsonl.lines().filter(line -> !line.isBlank()).toList();
            DidResolver resolver = DidResolver.Companion.fromJsonL(entries);
            DidLogEntry latest = resolver.resolveLatest(true, null);
            // WebVH 1.0 stores the DID document directly in state; TDW 0.3 wraps it in value.
            DidVerificationDocument document = latest instanceof DidLogEntry.WebVH10
                    ? Kapun_crypto_rust_jvmKt.parseDidVerificationDocument(latest.getState())
                    : latest.doc();
            if (document == null) {
                throw new IOException("Verified DID log does not contain a valid DID document");
            }
            return document;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Failed to verify DID document: " + ex.getMessage(), ex);
        }
    }

    /** Transforms a did:webvh or did:tdw DID to an HTTPS URL according to the spec. */
    public static String transformDidToHttpsUrl(String did) throws IOException {
        if (!isSupportedDid(did)) {
            throw new IOException("Unsupported or malformed DID: " + did);
        }

        int prefixEndIdx = did.indexOf(':', 4) + 1;
        String methodSpecificId = did.substring(prefixEndIdx);

        int scidEndIdx = methodSpecificId.indexOf(':');
        if (scidEndIdx == -1 || scidEndIdx == methodSpecificId.length() - 1) {
            throw new IOException("Invalid method-specific ID, missing SCID: " + methodSpecificId);
        }

        String withoutScid = methodSpecificId.substring(scidEndIdx + 1);
        String path = withoutScid.replace(':', '/');
        String httpsUrl;

        if (!path.contains("/")) {
            httpsUrl = "https://" + path + "/.well-known/did.jsonl";
        } else {
            httpsUrl = "https://" + path + "/did.jsonl";
        }

        return URLDecoder.decode(httpsUrl, StandardCharsets.UTF_8);
    }

    /** Uses RestTemplate to fetch the raw DID JSONL document. */
    public static String fetchDidJsonl(String url) throws IOException {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IOException("Failed to fetch DID document from " + url);
            }
            return response.getBody();
        } catch (Exception ex) {
            throw new IOException("Failed to fetch or parse DID document: " + ex.getMessage(), ex);
        }
    }
}
