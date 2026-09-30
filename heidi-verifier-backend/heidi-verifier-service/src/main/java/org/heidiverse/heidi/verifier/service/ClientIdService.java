// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidCertChainException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

@Service
public class ClientIdService {

    /**
     * Signs an authorization request with the verifier identity's key.
     *
     * <p>Certificate-based schemes carry the identity's chain in {@code x5c}. The verifier holds no
     * certificate authority of its own: the chain comes from the platform - issued by the local
     * development CA, a pilot CA, or a CA on a Trusted List - so a missing chain is an error.
     */
    public JWT wrapInJwt(
            final Map<String, Object> payload,
            final ClientIdScheme clientIdScheme,
            final List<Base64> certificateChain,
            final JWSSigner signer)
            throws ParseException, JOSEException {
        return wrapInJwt(payload, clientIdScheme, null, certificateChain, signer);
    }

    public JWT wrapInJwt(
            final Map<String, Object> payload,
            final ClientIdScheme clientIdScheme,
            final String headerKid,
            final List<Base64> certificateChain,
            final JWSSigner signer)
            throws ParseException, JOSEException {

        // build JOSE header
        JWSHeader.Builder builder =
                new JWSHeader.Builder(
                        signer.supportedJWSAlgorithms().iterator().next());

        // Add typ in header as specified in:
        // https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-authorization-request
        builder.type(new JOSEObjectType("oauth-authz-req+jwt"));
        if (headerKid != null && !headerKid.isBlank()) builder.keyID(headerKid);

        switch (clientIdScheme) {
            case X509_SAN_DNS, X509_HASH:
                if (certificateChain == null || certificateChain.isEmpty()) {
                    throw new InvalidCertChainException(
                            clientIdScheme.getClientIdentifierScheme()
                                    + " requires the verifier identity's certificate chain");
                }
                builder.x509CertChain(certificateChain);
                break;
            case DECENTRALIZED_IDENTIFIER:
                // The DID key id in the selected public JWK binds the request to the identity.
                break;
            case OPENID_FEDERATION:
                // Wallets resolve the verifier's federation entity configuration from the
                // openid_federation client identifier and obtain the request key from its JWKS.
                break;
        }

        final var header = builder.build();
        final var claims = JWTClaimsSet.parse(payload);

        // wrap and sign
        final var signedJwt = new SignedJWT(header, claims);
        signedJwt.sign(signer);

        return signedJwt;
    }

    public enum ClientIdScheme {
        X509_SAN_DNS("x509_san_dns"),
        X509_HASH("x509_hash"),
        DECENTRALIZED_IDENTIFIER("decentralized_identifier"),
        OPENID_FEDERATION("openid_federation");

        private final String clientIdentifierScheme;

        ClientIdScheme(final String clientIdentifierScheme) {
            this.clientIdentifierScheme = clientIdentifierScheme;
        }

        public String getClientIdentifierScheme() {
            return clientIdentifierScheme;
        }

        public static ClientIdScheme fromIdentifier(String identifier) {
            return switch (identifier) {
                case "x509_san_dns" -> X509_SAN_DNS;
                case "x509_hash" -> X509_HASH;
                case "decentralized_identifier" -> DECENTRALIZED_IDENTIFIER;
                case "openid_federation" -> OPENID_FEDERATION;
                default -> throw new IllegalArgumentException(
                        "Unsupported verifier client_id scheme: " + identifier);
            };
        }
    }
}
