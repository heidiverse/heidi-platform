// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.vp;

import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.verifier.model.validation.ValidationMode;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.util.List;
import java.util.Map;

public record VerificationRequestData(
        String nonce,
        String clientId,
        String responseMode,
        String redirectUri,
        String schemaLookup,
        String verificationKeys,
        String provingKeys,
        String zkpDefinition,
        DcqlQuery dcqlQuery,
        String scope,
        boolean includeDcqlQuery,
        List<String> transactionData,
        String validationLogic,
        ValidationMode validationMode,
        String tenantId,
        Map<String, String> clientMetadata,
        String signingIdentity,
        String signingKeyId,
        String signingTrustSystem,
        String clientIdScheme,
        OID4VPVersion OID4VPVersion,
        TrustConfiguration trustConfiguration,
        List<VerifierAttestation> verifierAttestations,
        boolean storeVpToken,
        String signingSnapshot,
        String presentationProfileId) {

    public VerificationRequestData(
            String nonce,
            String clientId,
            String responseMode,
            String redirectUri,
            String schemaLookup,
            String verificationKeys,
            String provingKeys,
            String zkpDefinition,
            DcqlQuery dcqlQuery,
            String scope,
            List<String> transactionData,
            String validationLogic,
            ValidationMode validationMode,
            String tenantId,
            Map<String, String> clientMetadata,
            String signingIdentity,
            String signingKeyId,
            String signingTrustSystem,
            String clientIdScheme,
            OID4VPVersion OID4VPVersion,
            TrustConfiguration trustConfiguration,
            List<VerifierAttestation> verifierAttestations,
            boolean storeVpToken,
            String signingSnapshot,
            String presentationProfileId) {
        this(nonce, clientId, responseMode, redirectUri, schemaLookup, verificationKeys,
                provingKeys, zkpDefinition, dcqlQuery, scope, false, transactionData,
                validationLogic, validationMode, tenantId, clientMetadata, signingIdentity,
                signingKeyId, signingTrustSystem, clientIdScheme, OID4VPVersion,
                trustConfiguration,
                verifierAttestations, storeVpToken, signingSnapshot, presentationProfileId);
    }
}
