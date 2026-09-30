// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.kapunsdk.credentials.ClaimsPointerKt;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uniffi.kapun_dcql_rust.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DcqlQueryService {

    private final String platformPublicBaseUrl;

    public DcqlQueryService(@Value("${heidi.platform.public-base-url}") String platformPublicBaseUrl) {
        this.platformPublicBaseUrl = platformPublicBaseUrl;
    }

    public DcqlQuery generate(ProofSchemeResponse proofSchema, OID4VPVersion oid4vpVersion)
            throws VctNotFoundException, DoctypeNotFoundException {
        List<CredentialQuery> credentials = new ArrayList<>();
        List<CredentialSetQuery> credentialSets = new ArrayList<>();

        for (var credentialScheme : proofSchema.credentialSchemes()) {
            var attributes = credentialScheme.attributes();
            var issuerSettings = credentialScheme.issuerSettings();
            String credentialIdentifier = credentialScheme.credentialIdentifier();
            String version = credentialScheme.version();

            String namespace =
                    issuerSettings != null
                                    && issuerSettings.namespace() != null
                                    && !issuerSettings.namespace().isEmpty()
                            ? issuerSettings.namespace()
                            : "org.heidiverse.heidi.entity.dev.1";
            String doctype =
                    issuerSettings != null
                                    && issuerSettings.doctype() != null
                                    && !issuerSettings.doctype().isEmpty()
                            ? issuerSettings.doctype()
                            : String.format(
                                    "org.heidiverse.heidi.%s",
                                    credentialIdentifier.replace(" ", ".").replace("-", "."));
            String vct =
                    issuerSettings != null
                                    && issuerSettings.vct() != null
                                    && !issuerSettings.vct().isEmpty()
                            ? issuerSettings.vct()
                            : schemaUrl(credentialIdentifier, version);
            String bbsCredentialType = schemaUrl(credentialIdentifier, version);

            var claimsSdJwt = new ArrayList<ClaimsQuery>();
            var claimsMDoc = new ArrayList<ClaimsQuery>();
            var claimsBbs = new ArrayList<ClaimsQuery>();
            var claimsW3c = new ArrayList<ClaimsQuery>();
            var claimsOpenBadges = new ArrayList<ClaimsQuery>();
            boolean requireCryptographicKeyBinding =
                    issuerSettings == null
                            || !issuerSettings.issuerKeyType().equals("NO_KEY_CLAIM_BINDING");

            for (var attribute : attributes) {
                String sdJwtName = override(attribute, "SD_JWT", attribute.name());
                claimsSdJwt.add(claim(attribute, List.of(sdJwtName.split("\\."))));

                String mDocName = override(attribute, "MSO_MDOC", attribute.name());
                claimsMDoc.add(claim(attribute, List.of(namespace, mDocName)));

                String bbsName =
                        override(attribute, "BBS", "http://schema.org/" + attribute.name());
                claimsBbs.add(claim(attribute, List.of(bbsName)));

                String w3cName = override(attribute, "W3C_VCDM", attribute.name());
                claimsW3c.add(claim(attribute, List.of("credentialSubject", w3cName)));

                String openBadgesName = override(attribute, "OPEN_BADGES", attribute.name());
                claimsOpenBadges.add(
                        claim(attribute, List.of("credentialSubject", openBadgesName)));
            }

            claimsW3c.add(typeClaim());
            claimsOpenBadges.add(typeClaim());

            Boolean multipleCredentials =
                    oid4vpVersion.getVersion() >= OID4VPVersion.DRAFT_26.getVersion()
                            ? false
                            : null;
            var options = new ArrayList<List<String>>();

            if (supports(issuerSettings, "SD_JWT")) {
                String id = credentialIdentifier + "_dc__sd-jwt";
                credentials.add(
                        new CredentialQuery(
                                id,
                                "dc+sd-jwt",
                                multipleCredentials,
                                new Meta.SdjwtVc(List.of(vct)),
                                authorities(proofSchema, oid4vpVersion, id),
                                requireCryptographicKeyBinding,
                                claimsSdJwt,
                                null));
                options.add(List.of(id));
            }

            if (supports(issuerSettings, "MSO_MDOC")) {
                String id = credentialIdentifier + "_mso_mdoc";
                credentials.add(
                        new CredentialQuery(
                                id,
                                "mso_mdoc",
                                multipleCredentials,
                                new Meta.IsoMdoc(doctype),
                                authorities(proofSchema, oid4vpVersion, id),
                                true,
                                claimsMDoc,
                                null));
                options.add(List.of(id));
            }

            if (supports(issuerSettings, "ZKP_VC")) {
                String id = credentialIdentifier + "_bbs-termwise";
                credentials.add(
                        new CredentialQuery(
                                id,
                                "bbs-termwise",
                                multipleCredentials,
                                new Meta.W3c(List.of(bbsCredentialType)),
                                authorities(proofSchema, oid4vpVersion, id),
                                requireCryptographicKeyBinding,
                                claimsBbs,
                                null));
                options.add(List.of(id));
            }

            if (supports(issuerSettings, "W3C_VCDM")) {
                String id = credentialIdentifier + "_w3c-vcdm";
                credentials.add(
                        new CredentialQuery(
                                id,
                                "vc+sd-jwt",
                                multipleCredentials,
                                null,
                                authorities(proofSchema, oid4vpVersion, id),
                                requireCryptographicKeyBinding,
                                claimsW3c,
                                null));
                options.add(List.of(id));
            }

            if (supports(issuerSettings, "OPENBADGES")) {
                String id = credentialIdentifier + "_open-badges";
                credentials.add(
                        new CredentialQuery(
                                id,
                                "ldp_vc",
                                multipleCredentials,
                                null,
                                authorities(proofSchema, oid4vpVersion, id),
                                false,
                                claimsOpenBadges,
                                null));
                options.add(List.of(id));
            }

            credentialSets.add(new CredentialSetQuery(options, true, null));
        }

        return new DcqlQuery(credentials, credentialSets);
    }

    private String schemaUrl(String identifier, String version) {
        return String.format("%s/public/v2/schema/%s/%s", platformPublicBaseUrl, identifier, version);
    }

    private static String override(
            ProofSchemeResponse.Attribute attribute, String format, String fallback) {
        if (attribute.attributeNameOverrides() == null) return fallback;

        String override = attribute.attributeNameOverrides().get(format);
        return override == null ? fallback : override;
    }

    private static ClaimsQuery claim(
            ProofSchemeResponse.Attribute attribute, List<String> pathParts) {
        return new ClaimsQuery(
                String.valueOf(attribute.id()),
                Objects.requireNonNull(ClaimsPointerKt.toClaimsPointer(pathParts)).getPath(),
                null);
    }

    private static ClaimsQuery typeClaim() {
        return new ClaimsQuery(
                null,
                Objects.requireNonNull(ClaimsPointerKt.toClaimsPointer(List.of("type"))).getPath(),
                null);
    }

    private static boolean supports(
            ProofSchemeResponse.IssuerSettings issuerSettings, String credentialType) {
        return issuerSettings == null
                || issuerSettings.supportedCredentialTypes() == null
                || issuerSettings.supportedCredentialTypes().isEmpty()
                || issuerSettings.supportedCredentialTypes().contains(credentialType);
    }

    private static List<TrustedAuthority> authorities(
            ProofSchemeResponse proofSchema, OID4VPVersion oid4vpVersion, String credentialId) {
        if (oid4vpVersion.getVersion() < 25 || proofSchema.trustedAuthorities() == null) return null;

        var authorities =
                proofSchema.trustedAuthorities().stream()
                        .filter(
                                authority ->
                                        authority.credentialId() == null
                                                || authority.credentialId().isBlank()
                                                || authority.credentialId().equals(credentialId))
                        .map(authority -> new TrustedAuthority(authority.type(), authority.values()))
                        .toList();
        return authorities.isEmpty() ? null : authorities;
    }
}
