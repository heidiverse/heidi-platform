// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import com.nimbusds.jose.jwk.JWK;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.kapunsdk.trust.did.DidResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Checks published material before a Swiss slot can start using a version. */
@Service
public class SwissKeyPublication {
    private static final String PREFIX = "did:webvh:";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient client;

    public SwissKeyPublication(RestClient.Builder builder) {
        client = builder.clone().build();
    }

    public void requirePublished(String did, String publicJwk) {
        try {
            var log = client.get().uri(logUrl(did)).retrieve().body(String.class);
            if (log == null) throw new IllegalArgumentException("DID history is empty");
            requireMethod(did, publicJwk, verifiedDocument(log));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Publish this key version in the Swiss DID before assigning or activating it: "
                    + exception.getMessage(), exception);
        }
    }

    static JsonNode verifiedDocument(String log) {
        // Resolve with proof verification; the last JSON line alone is not authoritative.
        var resolver = DidResolver.Companion.fromJsonL(log.lines().filter(line -> !line.isBlank()).toList());
        var latest = resolver.resolveLatest(true, null);
        JsonNode state = JSON.valueToTree(org.kapunsdk.util.extensions.ValueExtensionKt.toPlainObject(latest.getState()));
        return state.has("value") ? state.get("value") : state;
    }

    static void requireMethod(String did, String publicJwk, JsonNode document) throws java.text.ParseException, com.nimbusds.jose.JOSEException {
        var expected = JWK.parse(publicJwk);
        if (!did.equals(document.path("id").asText()) || expected.getKeyID() == null) {
            throw new IllegalArgumentException("DID document or key identifier does not match");
        }
        var methodId = did + "#" + expected.getKeyID();
        for (var method : document.path("verificationMethod")) {
            if (!methodId.equals(method.path("id").asText()) || !method.has("publicKeyJwk")) continue;
            if (expected.computeThumbprint().equals(JWK.parse(method.get("publicKeyJwk").toString()).computeThumbprint())) return;
        }
        throw new IllegalArgumentException("DID has no matching verification method " + methodId);
    }

    static URI logUrl(String did) {
        if (did == null || !did.startsWith(PREFIX)) throw new IllegalArgumentException("Swiss DID must use did:webvh");
        var parts = did.substring(PREFIX.length()).split(":", -1);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) throw new IllegalArgumentException("Invalid Swiss DID");
        var path = String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        var url = URI.create("https://" + URLDecoder.decode(path, StandardCharsets.UTF_8)
                + (parts.length == 2 ? "/.well-known" : "") + "/did.jsonl");
        if (url.getHost() == null || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("Invalid Swiss DID location");
        }
        return url;
    }
}
