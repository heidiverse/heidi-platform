// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.heidiverse.heidi.entity.model.issuer.PrivateKeyFormat;
import org.springframework.stereotype.Service;

/** Converts supported import transports to the provider-neutral private JWK contract. */
@Service
public class PrivateKeyImportService {
    public ImportedPrivateKey read(
            PrivateKeyFormat format,
            String material,
            String password,
            String algorithm,
            String keyId) {
        if (format == null) throw new IllegalArgumentException("Private key format is required");
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("Private key material is required");
        }

        return switch (format) {
            case JWK -> new ImportedPrivateKey(readJwk(material, algorithm, keyId), List.of());
            case JWKS -> new ImportedPrivateKey(readJwks(material, algorithm, keyId), List.of());
            case PEM -> new ImportedPrivateKey(readPem(material, algorithm, keyId), List.of());
            case PKCS12 -> readPkcs12(material, password, algorithm, keyId);
        };
    }

    private String readJwk(String material, String algorithm, String keyId) {
        try {
            return normalize(JWK.parse(material), algorithm, keyId);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not import private JWK", exception);
        }
    }

    private String readJwks(String material, String algorithm, String keyId) {
        try {
            var privateKeys = JWKSet.parse(material).getKeys().stream()
                    .filter(JWK::isPrivate)
                    .toList();
            if (privateKeys.size() != 1) {
                throw new IllegalArgumentException(
                        "JWK Set must contain exactly one private key");
            }
            return normalize(privateKeys.getFirst(), algorithm, keyId);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not import private JWK Set", exception);
        }
    }

    private String normalize(JWK key, String algorithm, String keyId) throws Exception {
        if (!key.isPrivate()) throw new IllegalArgumentException("JWK does not contain a private key");

        var json = new HashMap<>(key.toJSONObject());
        json.put("kid", keyId);
        json.put("alg", algorithm);
        return JWK.parse(json).toJSONString();
    }

    private String readPem(String material, String algorithm, String keyId) {
        try (var parser = new PEMParser(new StringReader(material))) {
            var parsed = parser.readObject();
            if (parsed == null) {
                throw new IllegalArgumentException("PEM input does not contain a private key");
            }

            var converter = new JcaPEMKeyConverter();
            PrivateKey privateKey;
            PublicKey publicKey = null;
            if (parsed instanceof PEMKeyPair keyPair) {
                var converted = converter.getKeyPair(keyPair);
                privateKey = converted.getPrivate();
                publicKey = converted.getPublic();
            } else if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                privateKey = converter.getPrivateKey(privateKeyInfo);
            } else {
                throw new IllegalArgumentException(
                        "PEM input must contain an unencrypted PKCS#8 or key pair");
            }
            if (privateKey.getEncoded() == null) {
                throw new IllegalArgumentException("PEM private key cannot be exported for import");
            }

            if (publicKey == null) publicKey = derivePublicKey(privateKey, algorithm);
            return privateJwk(privateKey, publicKey, algorithm, keyId);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not import PEM private key. Check the format and key algorithm", exception);
        }
    }

    private ImportedPrivateKey readPkcs12(
            String material, String rawPassword, String algorithm, String keyId) {
        var password = rawPassword == null ? new char[0] : rawPassword.toCharArray();
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(Base64.getDecoder().decode(material)), password);
            var aliases = keyStore.aliases();
            var privateKeyAliases = new ArrayList<String>();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) privateKeyAliases.add(alias);
            }
            if (privateKeyAliases.size() != 1) {
                throw new IllegalArgumentException(
                        "PKCS#12 must contain exactly one private-key entry");
            }

            var alias = privateKeyAliases.getFirst();
            Key key = keyStore.getKey(alias, password);
            if (!(key instanceof PrivateKey privateKey) || privateKey.getEncoded() == null) {
                throw new IllegalArgumentException("PKCS#12 entry is not an exportable private key");
            }
            var certificate = keyStore.getCertificate(alias);
            var publicKey = certificate == null
                    ? derivePublicKey(privateKey, algorithm)
                    : certificate.getPublicKey();
            var chain = keyStore.getCertificateChain(alias) == null
                    ? List.<String>of()
                    : Arrays.stream(keyStore.getCertificateChain(alias)).map(item -> {
                        try {
                            return Base64.getEncoder().encodeToString(item.getEncoded());
                        } catch (Exception exception) {
                            throw new IllegalArgumentException(
                                    "Could not read PKCS#12 certificate chain", exception);
                        }
                    }).toList();
            return new ImportedPrivateKey(
                    privateJwk(privateKey, publicKey, algorithm, keyId), chain);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not import PKCS#12 file. Check the password and key algorithm", exception);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private PublicKey derivePublicKey(PrivateKey privateKey, String algorithm) throws Exception {
        try (var keyPair = new uniffi.heidi_signing.JoseKeyPair(
                privateKey.getEncoded(), algorithm)) {
            return publicKey(JWK.parse(keyPair.toPublicJwk()));
        }
    }

    private PublicKey publicKey(JWK key) throws Exception {
        return switch (key.getKeyType().getValue()) {
            case "EC" -> key.toECKey().toPublicKey();
            case "RSA" -> key.toRSAKey().toPublicKey();
            case "OKP" -> key.toOctetKeyPair().toPublicKey();
            default -> throw new IllegalArgumentException(
                    "Unsupported private key type: " + key.getKeyType());
        };
    }

    private String privateJwk(
            PrivateKey privateKey, PublicKey publicKey, String algorithm, String keyId) {
        try {
            JWK jwk;
            if (privateKey instanceof ECPrivateKey ecPrivate
                    && publicKey instanceof ECPublicKey ecPublic) {
                jwk = new ECKey.Builder(Curve.forECParameterSpec(ecPublic.getParams()), ecPublic)
                        .privateKey(ecPrivate)
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else if (privateKey instanceof RSAPrivateKey rsaPrivate
                    && publicKey instanceof RSAPublicKey rsaPublic) {
                jwk = new RSAKey.Builder(rsaPublic)
                        .privateKey(rsaPrivate)
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else if (privateKey instanceof EdECPrivateKey edPrivate
                    && publicKey instanceof EdECPublicKey edPublic) {
                var curve = Curve.forStdName(edPublic.getParams().getName());
                var publicBytes = edPublicBytes(edPublic);
                var privateBytes = edPrivate.getBytes().orElseThrow(
                        () -> new IllegalArgumentException("EdEC private key has no raw key bytes"));
                if (!Curve.Ed25519.equals(curve)
                        || publicBytes.length != 32
                        || privateBytes.length != 32) {
                    throw new IllegalArgumentException("Only Ed25519 keys are supported for EdDSA");
                }
                jwk = new OctetKeyPair.Builder(curve, Base64URL.encode(publicBytes))
                        .d(Base64URL.encode(privateBytes))
                        .keyID(keyId)
                        .algorithm(JWSAlgorithm.parse(algorithm))
                        .build();
            } else {
                throw new IllegalArgumentException(
                        "Unsupported private key type: " + privateKey.getAlgorithm());
            }
            return jwk.toJSONString();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not convert private key to JWK", exception);
        }
    }

    private byte[] edPublicBytes(EdECPublicKey publicKey) {
        var point = publicKey.getPoint();
        var y = point.getY().toByteArray();
        var encoded = new byte[32];
        for (int index = 0; index < y.length; index++) {
            var source = y.length - 1 - index;
            if (index >= encoded.length) {
                if (y[source] != 0) {
                    throw new IllegalArgumentException("EdEC public key coordinate is too large");
                }
                break;
            }
            encoded[index] = y[source];
        }
        if (point.isXOdd()) encoded[encoded.length - 1] |= (byte) 0x80;
        return encoded;
    }

    public record ImportedPrivateKey(String privateJwk, List<String> certificateChain) {}
}
