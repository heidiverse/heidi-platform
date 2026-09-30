// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.entity;

import jakarta.persistence.*;

import org.heidiverse.heidi.verifier.model.converter.DcqlQueryConverter;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.heidiverse.heidi.verifier.model.validation.ValidationMode;
import org.heidiverse.heidi.verifier.model.vp.VerifierAttestation;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "t_verification_request")
public class VerificationRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "pk_verification_request_id", nullable = false, unique = true)
    private Integer id;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "nonce", nullable = false)
    private String nonce;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "response_mode", nullable = false)
    private String responseMode;

    @Column(name = "status", nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "redirect_uri")
    private String redirectUri;

    @Column(name = "zkp_definition")
    private String zkpDefinition;

    @Column private String verifyingKey;
    @Column private String provingKey;

    @Column(name = "schema_lookup")
    private String schemaLookup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = DcqlQueryConverter.class)
    @Column(name = "dcql_query")
    private DcqlQuery dcqlQuery;

    @Column(name = "scope")
    private String scope;

    @Column(name = "include_dcql_query", nullable = false)
    private boolean includeDcqlQuery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transaction_data", columnDefinition = "jsonb")
    private List<String> transactionData;

    @Column(name = "validation_logic", columnDefinition = "text")
    private String validationLogic;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_mode", nullable = false)
    private ValidationMode validationMode = ValidationMode.DISABLED;

    @Column(name = "validation_result")
    private Boolean validationResult;

    @Column(name = "tenant_id")
    private String tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_metadata", columnDefinition = "jsonb")
    private Map<String, String> clientMetadata;

    @Column(name = "signing_flow_released", nullable = false)
    private boolean signingFlowReleased;

    public void markSigningFlowReleased() { signingFlowReleased = true; }

    // Public provider configuration is immutable for the lifetime of this request.
    @Column(name = "signing_snapshot", columnDefinition = "text")
    private String signingSnapshot;

    public String getSigningSnapshot() { return signingSnapshot; }
    public void setSigningSnapshot(String value) { signingSnapshot = value; }

    @Column(name = "signing_identity")
    private String signingIdentity;

    @Column(name = "signing_key_id")
    private String signingKeyId;

    @Column(name = "signing_trust_system")
    private String signingTrustSystem;

    /** Immutable presentation profile selected when this request was created. */
    @Column(name = "presentation_profile_id", nullable = false)
    private String presentationProfileId;

    @Column(name = "client_id_scheme")
    private String clientIdScheme;

    @Column(name = "trustframework")
    @Enumerated(EnumType.STRING)
    private TrustFrameworkType trustFramework;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "eudi_trust_anchors", columnDefinition = "jsonb")
    private List<String> eudiTrustAnchors;

    @Column(name = "swiss_trust_anchor")
    private String swissTrustAnchor;

    @Column(name = "swiss_trust_registry_base_url")
    private String swissTrustRegistryBaseUrl;

    @Column(name = "response_encryption_key_id")
    private String responseEncryptionKeyId;

    @Column(name = "response_encryption_public_jwk", columnDefinition = "text")
    private String responseEncryptionPublicJwk;

    @Column(name = "response_encryption_private_jwk", columnDefinition = "text")
    private String responseEncryptionPrivateJwk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verifier_attestations", columnDefinition = "jsonb")
    private List<VerifierAttestation> verifierAttestations;

    @Column(name = "store_vp_token", nullable = false)
    private boolean storeVpToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "oid4vp_draft_version")
    private OID4VPVersion OID4VPVersion;

    public static VerificationRequestEntity from(
            final String requestId,
            final String transactionId,
            final String nonce,
            final String clientId,
            final String responseMode,
            final Instant expiry,
            final String redirectUri,
            final String schemaLookup,
            final OID4VPVersion OID4VPVersion,
            final String verifyingKey,
            final String provingKey,
            final String zkpDefinition,
            final DcqlQuery dcqlQuery,
            final List<String> transactionData,
            final String validationLogic,
            final ValidationMode validationMode,
            final String tenantId,
            final Map<String, String> clientMetadata,
            final String signingIdentity,
            final String signingKeyId,
            final String signingTrustSystem,
            final String clientIdScheme,
            final TrustFrameworkType trustFramework,
            final List<String> eudiTrustAnchors,
            final String swissTrustAnchor,
            final String swissTrustRegistryBaseUrl,
            final List<VerifierAttestation> veriferAttestations,
            final boolean storeVpToken) {
        return from(requestId, transactionId, nonce, clientId, responseMode, expiry, redirectUri,
                schemaLookup, OID4VPVersion, verifyingKey, provingKey, zkpDefinition, dcqlQuery,
                null, false,
                transactionData, validationLogic, validationMode, tenantId, clientMetadata,
                signingIdentity, signingKeyId, signingTrustSystem, clientIdScheme, trustFramework,
                eudiTrustAnchors, swissTrustAnchor, swissTrustRegistryBaseUrl,
                veriferAttestations, storeVpToken, null);
    }

    public static VerificationRequestEntity from(
            final String requestId,
            final String transactionId,
            final String nonce,
            final String clientId,
            final String responseMode,
            final Instant expiry,
            final String redirectUri,
            final String schemaLookup,
            final OID4VPVersion OID4VPVersion,
            final String verifyingKey,
            final String provingKey,
            final String zkpDefinition,
            final DcqlQuery dcqlQuery,
            final String scope,
            final List<String> transactionData,
            final String validationLogic,
            final ValidationMode validationMode,
            final String tenantId,
            final Map<String, String> clientMetadata,
            final String signingIdentity,
            final String signingKeyId,
            final String signingTrustSystem,
            final String clientIdScheme,
            final TrustFrameworkType trustFramework,
            final List<String> eudiTrustAnchors,
            final String swissTrustAnchor,
            final String swissTrustRegistryBaseUrl,
            final List<VerifierAttestation> veriferAttestations,
            final boolean storeVpToken,
            final String presentationProfileId) {
        return from(requestId, transactionId, nonce, clientId, responseMode, expiry, redirectUri,
                schemaLookup, OID4VPVersion, verifyingKey, provingKey, zkpDefinition, dcqlQuery,
                scope, false, transactionData, validationLogic, validationMode, tenantId,
                clientMetadata, signingIdentity, signingKeyId, signingTrustSystem, clientIdScheme,
                trustFramework, eudiTrustAnchors, swissTrustAnchor, swissTrustRegistryBaseUrl,
                veriferAttestations, storeVpToken, presentationProfileId);
    }

    public static VerificationRequestEntity from(
            final String requestId,
            final String transactionId,
            final String nonce,
            final String clientId,
            final String responseMode,
            final Instant expiry,
            final String redirectUri,
            final String schemaLookup,
            final OID4VPVersion OID4VPVersion,
            final String verifyingKey,
            final String provingKey,
            final String zkpDefinition,
            final DcqlQuery dcqlQuery,
            final String scope,
            final boolean includeDcqlQuery,
            final List<String> transactionData,
            final String validationLogic,
            final ValidationMode validationMode,
            final String tenantId,
            final Map<String, String> clientMetadata,
            final String signingIdentity,
            final String signingKeyId,
            final String signingTrustSystem,
            final String clientIdScheme,
            final TrustFrameworkType trustFramework,
            final List<String> eudiTrustAnchors,
            final String swissTrustAnchor,
            final String swissTrustRegistryBaseUrl,
            final List<VerifierAttestation> veriferAttestations,
            final boolean storeVpToken,
            final String presentationProfileId) {
        final var entity = new VerificationRequestEntity();
        entity.requestId = requestId;
        entity.transactionId = transactionId;
        entity.nonce = nonce;
        entity.clientId = clientId;
        entity.responseMode = responseMode;
        entity.status = "NOT_STARTED";
        entity.expiresAt = expiry;
        entity.redirectUri = redirectUri;
        entity.schemaLookup = schemaLookup;
        entity.OID4VPVersion = OID4VPVersion;
        entity.verifyingKey = verifyingKey;
        entity.provingKey = provingKey;
        entity.zkpDefinition = zkpDefinition;
        entity.dcqlQuery = dcqlQuery;
        entity.scope = scope;
        entity.includeDcqlQuery = includeDcqlQuery;
        entity.transactionData = transactionData;
        entity.validationLogic = validationLogic;
        entity.validationMode = validationMode == null ? ValidationMode.DISABLED : validationMode;
        entity.tenantId = tenantId;
        entity.clientMetadata = clientMetadata;
        entity.signingIdentity = signingIdentity;
        entity.signingKeyId = signingKeyId;
        entity.signingTrustSystem = signingTrustSystem;
        entity.presentationProfileId = presentationProfileId;
        entity.clientIdScheme = clientIdScheme;
        entity.trustFramework = trustFramework;
        entity.eudiTrustAnchors = eudiTrustAnchors;
        entity.swissTrustAnchor = swissTrustAnchor;
        entity.swissTrustRegistryBaseUrl = swissTrustRegistryBaseUrl;
        entity.verifierAttestations = veriferAttestations;
        entity.storeVpToken = storeVpToken;
        return entity;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getId() {
        return id;
    }

    public String getNonce() {
        return nonce;
    }

    public String getClientId() {
        return clientId;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getProvingKey() {
        return provingKey;
    }

    public String getVerifyingKey() {
        return verifyingKey;
    }

    public String getZkpDefinition() {
        return zkpDefinition;
    }

    public String getSchemaLookup() {
        return schemaLookup;
    }

    public void setSchemaLookup(String schemaLookup) {
        this.schemaLookup = schemaLookup;
    }

    public DcqlQuery getDcqlQuery() {
        return dcqlQuery;
    }

    public String getScope() {
        return scope;
    }

    public boolean isIncludeDcqlQuery() {
        return includeDcqlQuery;
    }

    public List<String> getTransactionData() {
        return transactionData;
    }

    public String getValidationLogic() {
        return validationLogic;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public Boolean getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(Boolean validationResult) {
        this.validationResult = validationResult;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Map<String, String> getClientMetadata() {
        return clientMetadata;
    }

    public String getSigningIdentity() {
        return signingIdentity;
    }

    public String getSigningKeyId() {
        return signingKeyId;
    }

    public String getSigningTrustSystem() {
        return signingTrustSystem;
    }

    public String getPresentationProfileId() {
        return presentationProfileId;
    }

    public String getClientIdScheme() {
        return clientIdScheme;
    }

    public TrustFrameworkType getTrustFramework() {
        return trustFramework;
    }

    public List<String> getEudiTrustAnchors() {
        return eudiTrustAnchors;
    }

    public String getSwissTrustAnchor() {
        return swissTrustAnchor;
    }

    public String getSwissTrustRegistryBaseUrl() {
        return swissTrustRegistryBaseUrl;
    }

    public List<VerifierAttestation> getVerifierAttestations() {
        return verifierAttestations;
    }

    public boolean isStoreVpToken() {
        return storeVpToken;
    }

    public String getResponseEncryptionKeyId() {
        return responseEncryptionKeyId;
    }

    public String getResponseEncryptionPublicJwk() {
        return responseEncryptionPublicJwk;
    }

    public String getResponseEncryptionPrivateJwk() {
        return responseEncryptionPrivateJwk;
    }

    public void setResponseEncryptionKey(
            final String keyId,
            final String publicJwk,
            final String encryptedPrivateJwk) {
        this.responseEncryptionKeyId = keyId;
        this.responseEncryptionPublicJwk = publicJwk;
        this.responseEncryptionPrivateJwk = encryptedPrivateJwk;
    }

    public void clearResponseEncryptionKey() {
        this.responseEncryptionKeyId = null;
        this.responseEncryptionPublicJwk = null;
        this.responseEncryptionPrivateJwk = null;
    }

    public OID4VPVersion getOID4VPVersion() {
        return OID4VPVersion;
    }
}
