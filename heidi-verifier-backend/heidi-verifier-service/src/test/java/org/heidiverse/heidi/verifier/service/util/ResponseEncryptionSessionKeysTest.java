// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.util;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseEncryptionSessionKeysTest {
    private static final String REQUEST_ID = "request-1";

    @Test
    void encryptsAndDecryptsARequestScopedPrivateKey() throws Exception {
        var service = new ResponseEncryptionSessionKeys(new byte[32]);
        var generated = service.generate(REQUEST_ID);
        var publicKey = (ECKey) JWK.parse(generated.publicJwk());
        var jwe = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                        .keyID(generated.keyId()).build(),
                new Payload("{}"));
        jwe.encrypt(new ECDHEncrypter(publicKey.toECPublicKey()));

        var decrypted = JWEObject.parse(jwe.serialize());
        var privateKey = service.decrypt(
                new ResponseEncryptionSessionKeys.ResponseKey(
                        REQUEST_ID,
                        generated.keyId(),
                        generated.publicJwk(),
                        generated.encryptedPrivateJwk()),
                decrypted);

        assertEquals(generated.keyId(), privateKey.getKeyID());
        assertEquals("{}", decrypted.getPayload().toString());
    }

    @Test
    void bindsTheStoredPrivateKeyToItsRequest() {
        var service = new ResponseEncryptionSessionKeys(HexFormat.of().parseHex(
                "0000000000000000000000000000000000000000000000000000000000000000"));
        var generated = service.generate(REQUEST_ID);
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(
                new ResponseEncryptionSessionKeys.ResponseKey(
                        "other-request", generated.keyId(), generated.publicJwk(),
                        generated.encryptedPrivateJwk()),
                new JWEObject(new JWEHeader.Builder(JWEAlgorithm.ECDH_ES,
                        EncryptionMethod.A256GCM).build(), new Payload("{}"))));
    }
}
