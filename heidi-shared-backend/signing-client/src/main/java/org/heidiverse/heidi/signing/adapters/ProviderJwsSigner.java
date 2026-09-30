// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.adapters;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;

/** Nimbus {@link JWSSigner} backed by the raw provider contract. */
public final class ProviderJwsSigner implements JWSSigner {
    private final SigningKeyProvider provider;
    private final SigningKeyRef key;
    private final Set<JWSAlgorithm> supportedAlgorithms;
    private final JCAContext jcaContext = new JCAContext();

    public ProviderJwsSigner(SigningKeyProvider provider, SigningKeyRef key) {
        this.provider = provider;
        this.key = key;
        var algorithms = new LinkedHashSet<JWSAlgorithm>();
        provider.supportedAlgorithms().forEach(algorithm -> algorithms.add(new JWSAlgorithm(algorithm)));
        this.supportedAlgorithms = Collections.unmodifiableSet(algorithms);
    }

    @Override
    public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
        if (header == null || header.getAlgorithm() == null) {
            throw new JOSEException("JWS header algorithm is required");
        }
        if (!key.algorithm().equals(header.getAlgorithm().getName())) {
            throw new JOSEException("JWS algorithm does not match the signing key");
        }
        if (!supportedAlgorithms.contains(header.getAlgorithm())) {
            throw new JOSEException("Provider does not support " + header.getAlgorithm());
        }
        try {
            return Base64URL.encode(provider.sign(key, signingInput));
        } catch (RuntimeException exception) {
            throw new JOSEException("Provider could not sign JWS input", exception);
        }
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return supportedAlgorithms;
    }

    @Override
    public JCAContext getJCAContext() {
        return jcaContext;
    }
}
