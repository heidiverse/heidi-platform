// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyConverter;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * kapun's JWK-based signature check must use the JWS header algorithm and enforce the JWK's
 * signing-use metadata.
 */
class JwsUtilTest {

    @Test
    void rs256MatchesNimbus() throws Exception {
        checkAsymmetric(JWSAlgorithm.RS256, rsaKey());
    }

    @Test
    void rs384MatchesNimbus() throws Exception {
        // No explicit 'alg' on the JWK: proves the header alg is used, not kapun's RSA default.
        checkAsymmetric(JWSAlgorithm.RS384, rsaKey());
    }

    @Test
    void ps256MatchesNimbus() throws Exception {
        checkAsymmetric(JWSAlgorithm.PS256, rsaKey());
    }

    @Test
    void es256MatchesNimbus() throws Exception {
        checkAsymmetric(JWSAlgorithm.ES256, ecKey(Curve.P_256));
    }

    @Test
    void es384MatchesNimbus() throws Exception {
        checkAsymmetric(JWSAlgorithm.ES384, ecKey(Curve.P_384));
    }

    @Test
    void rejectsAJwkMarkedForEncryption() throws Exception {
        var key = new ECKeyGenerator(Curve.P_256).keyUse(KeyUse.ENCRYPTION).generate();
        var jws = sign(JWSAlgorithm.ES256, key);

        assertFalse(JwsUtil.verify(jws, key.toPublicJWK()));
    }

    private void checkAsymmetric(JWSAlgorithm alg, JWK signingKey) throws Exception {
        var jws = sign(alg, signingKey);
        var publicKey = signingKey.toPublicJWK();

        assertEquals(nimbusVerifies(jws, publicKey), JwsUtil.verify(jws, publicKey), alg.getName());
        assertTrue(JwsUtil.verify(jws, publicKey), alg.getName() + ": valid signature rejected");
        assertFalse(
                JwsUtil.verify(tamper(jws), publicKey),
                alg.getName() + ": tampered signature accepted");
        assertFalse(
                JwsUtil.verify(jws, otherKey(signingKey).toPublicJWK()),
                alg.getName() + ": wrong key accepted");
    }

    private JWSObject sign(JWSAlgorithm alg, JWK key) throws Exception {
        // The payload must be JSON: kapun's JWT parser requires it, unlike Nimbus's raw verify().
        var jws = new JWSObject(new JWSHeader.Builder(alg).build(), new Payload(Map.of("foo", "bar")));
        jws.sign(signerFor(key));
        return JWSObject.parse(jws.serialize());
    }

    private JWSSigner signerFor(JWK key) throws Exception {
        return key instanceof RSAKey rsaKey ? new RSASSASigner(rsaKey) : new ECDSASigner((ECKey) key);
    }

    private boolean nimbusVerifies(JWSObject jws, JWK publicKey) throws Exception {
        var verifier = new DefaultJWSVerifierFactory()
                .createJWSVerifier(jws.getHeader(), KeyConverter.toJavaKeys(List.of(publicKey)).getFirst());
        return jws.verify(verifier);
    }

    private JWSObject tamper(JWSObject jws) throws Exception {
        var parts = jws.serialize().split("\\.");
        var signature = parts[2].toCharArray();
        var index = signature.length / 2;
        signature[index] = signature[index] == 'A' ? 'B' : 'A';
        return JWSObject.parse(parts[0] + "." + parts[1] + "." + new String(signature));
    }

    private JWK otherKey(JWK sameTypeAs) throws Exception {
        return sameTypeAs instanceof RSAKey ? rsaKey() : ecKey(((ECKey) sameTypeAs).getCurve());
    }

    private RSAKey rsaKey() throws Exception {
        return new RSAKeyGenerator(2048).generate();
    }

    private ECKey ecKey(Curve curve) throws Exception {
        return new ECKeyGenerator(curve).generate();
    }
}
