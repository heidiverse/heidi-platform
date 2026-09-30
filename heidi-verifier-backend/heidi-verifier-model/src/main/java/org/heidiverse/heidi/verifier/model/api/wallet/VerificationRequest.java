// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import org.heidiverse.heidi.verifier.model.converter.DcqlQueryConverter;
import org.heidiverse.heidi.verifier.model.converter.DcqlQueryDeserializer;
import org.heidiverse.heidi.verifier.model.converter.DcqlQuerySerializer;

import org.heidiverse.heidi.verifier.model.vp.VerifierAttestation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import jakarta.persistence.Convert;

import uniffi.kapun_dcql_rust.DcqlQuery;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationRequest {

    @JsonProperty(value = "client_id", required = true)
    private final String clientId;

    @JsonProperty(value = "response_uri", required = true)
    private final String responseUri;

    @JsonProperty(value = "response_type", required = true)
    private final String responseType;

    @JsonProperty(value = "response_mode", required = true)
    private final String responseMode;

    @JsonProperty(value = "nonce", required = true)
    private final String nonce;

    @JsonProperty(value = "state", required = true)
    private final String state;

    @JsonProperty(value = "aud", required = true)
    private final String aud;

    @JsonProperty(value = "client_metadata", required = true)
    private final ClientMetadata clientMetadata;

    @JsonProperty(value = "zkp")
    private final Zkp zkp;

    @Convert(converter = DcqlQueryConverter.class)
    @JsonProperty(value = "dcql_query")
    @JsonSerialize(using = DcqlQuerySerializer.class)
    @JsonDeserialize(using = DcqlQueryDeserializer.class)
    private final DcqlQuery dcqlQuery;

    @JsonProperty(value = "scope")
    private final String scope;

    @JsonProperty(value = "transaction_data")
    List<String> transactionData;

    @JsonProperty(value = "verifier_info")
    private final List<VerifierAttestation> verifierAttestations;

    private VerificationRequest(VerificationRequestBuilder builder) {
        this.clientId = builder.clientId;
        this.responseUri = builder.responseUri;
        this.responseType = builder.responseType;
        this.responseMode = builder.responseMode;
        this.state = builder.state;
        this.aud = builder.aud;
        this.nonce = builder.nonce;
        this.clientMetadata = builder.clientMetadata;
        this.zkp = builder.zkp;
        this.dcqlQuery = builder.dcqlQuery;
        this.scope = builder.scope;
        this.transactionData = builder.transactionData;
        this.verifierAttestations = builder.verifierAttestations;
    }

    public static VerificationRequestBuilder builder() {
        return new VerificationRequestBuilder();
    }

    public String getClientId() {
        return clientId;
    }

    public String getResponseUri() {
        return responseUri;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public String getNonce() {
        return nonce;
    }

    public String getState() {
        return state;
    }

    public String getAud() {
        return aud;
    }

    public Zkp getZkp() {
        return zkp;
    }

    public ClientMetadata getClientMetadata() {
        return clientMetadata;
    }

    public DcqlQuery getDcqlQuery() {
        return dcqlQuery;
    }

    public String getScope() {
        return scope;
    }

    public List<String> getTransactionData() {
        return transactionData;
    }

    public List<VerifierAttestation> getVerifierAttestations() {
        return verifierAttestations;
    }

    public static class VerificationRequestBuilder {
        private String clientId;
        private String responseUri;
        private String responseType;
        private String responseMode;
        private String nonce;
        private String state;
        private String aud;
        private ClientMetadata clientMetadata;
        private Zkp zkp;
        private DcqlQuery dcqlQuery;
        private String scope;
        private List<String> transactionData;
        private List<VerifierAttestation> verifierAttestations;

        public VerificationRequest build() {
            return new VerificationRequest(this);
        }

        public VerificationRequestBuilder withClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public VerificationRequestBuilder withResponseUri(String responseUri) {
            this.responseUri = responseUri;
            return this;
        }

        public VerificationRequestBuilder withResponseType(String responseType) {
            this.responseType = responseType;
            return this;
        }

        public VerificationRequestBuilder withResponseMode(String responseMode) {
            this.responseMode = responseMode;
            return this;
        }

        public VerificationRequestBuilder withNonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public VerificationRequestBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public VerificationRequestBuilder withAud(String aud) {
            this.aud = aud;
            return this;
        }

        public VerificationRequestBuilder withClientMetadata(ClientMetadata clientMetadata) {
            this.clientMetadata = clientMetadata;
            return this;
        }

        public VerificationRequestBuilder withZkp(Zkp zkp) {
            this.zkp = zkp;
            return this;
        }

        public VerificationRequestBuilder withDcqlQuery(DcqlQuery dcqlQuery) {
            this.dcqlQuery = dcqlQuery;
            return this;
        }

        public VerificationRequestBuilder withScope(String scope) {
            this.scope = scope;
            return this;
        }

        public VerificationRequestBuilder withTransactionData(List<String> transactionData) {
            this.transactionData = transactionData;
            return this;
        }

        public VerificationRequestBuilder withVeriferAttestation(
                List<VerifierAttestation> verifierAttestations) {
            this.verifierAttestations = verifierAttestations;
            return this;
        }
    }
}
