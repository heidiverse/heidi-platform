// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.service

import com.nimbusds.jose.jwk.JWK
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.heidiverse.heidi.issuer.data.IssuanceSessionRepository
import org.heidiverse.heidi.issuer.model.FlowVariant
import org.heidiverse.heidi.issuer.model.IssuanceStatus
import org.heidiverse.heidi.issuer.model.EnforcementMode
import org.heidiverse.heidi.issuer.model.TrustSystem
import org.heidiverse.heidi.issuer.model.api.*
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity
import org.heidiverse.heidi.issuer.service.SchemaMetadataService.CredentialFormat
import org.heidiverse.heidi.shared.oca.OcaFormat
import org.heidiverse.heidi.issuer.service.bbs.BbsCredentialIssuer
import org.kapunsdk.crypto.jwt.Jwt
import org.kapunsdk.crypto.jwt.JwtValidator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

private const val LEGACY_FORMAT_QUERY = "?format=legacy"

internal fun ocaRenderUrl(
    metadata: SchemaMetadataService.SchemaMetadata,
    publicUrl: String,
    userAgent: String?,
): String? {
    // swiyu wallets resolve OCA through type metadata; the render claim is legacy-only.
    val format = OcaFormat.select(null, userAgent, metadata.ocaFormat)
    val fileName = metadata.legacyOcaBundleFileName
        ?: metadata.ocaBundleFileName.takeIf { metadata.ocaFormat == OcaFormat.LEGACY }
    return fileName
        ?.takeIf { format == OcaFormat.LEGACY }
        ?.let {
            val suffix = if (metadata.ocaFormat == OcaFormat.SWIYU) LEGACY_FORMAT_QUERY else ""
            "${publicUrl.trimEnd('/')}/oca/$it.json$suffix"
        }
}

@Service
class IssuanceService(
    private val sessions: IssuanceSessionRepository,
    private val tokenDecoder: ProcessTokenDecoder,
    private val metadataService: SchemaMetadataService,
    private val credentialIssuer: KapunCredentialIssuer,
    private val signatureClient: SignatureClient,
    private val bbsCredentialIssuer: BbsCredentialIssuer,
    private val dpopService: DpopService,
    private val enforcement: EnforcementProperties,
    private val sessionCrypto: SessionCryptoService,
    private val transactionCodes: TransactionCodeService,
    private val deferredCredentials: DeferredCredentialClient,
    private val attributeCallbacks: AttributeCallbackClient,
    private val properties: IssuerProperties,
    private val credentialEncryption: CredentialEncryptionService,
    private val signingFlows: IssuerFlowService,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val objectMapper = ObjectMapper()

    fun issuanceProfileFor(issuerSlug: String, identifier: String, version: String): String =
        signatureClient.profileFor(issuerSlug, identifier, version)

    @org.springframework.scheduling.annotation.Scheduled(
        fixedDelayString = "\${heidi.issuer.signing-flow-release-interval-ms:30000}",
    )
    @Transactional
    fun releaseFinishedFlows() {
        // A completed credential may still be fetched or refreshed until its tokens expire.
        sessions.finishedSigningFlows(Instant.now()).forEach { session ->
            try {
                signingFlows.release(session.id)
                session.isSigningFlowReleased = true
                sessions.save(session)
            } catch (exception: RuntimeException) {
                org.slf4j.LoggerFactory.getLogger(IssuanceService::class.java)
                    .warn("Could not release issuance signing flow {}", session.id, exception)
            }
        }
    }

    @Transactional
    fun createOffer(
        issuerSlug: String,
        variant: FlowVariant,
        request: CredentialOfferRequest,
    ): CredentialOfferResponse {
        val data = request.token?.takeIf { it.isNotBlank() }?.let(tokenDecoder::decode)
            ?: throw IllegalArgumentException("token must be supplied")
        data.issuerSlug?.let { tokenIssuerSlug ->
            require(tokenIssuerSlug == issuerSlug) {
                "Process token issuer '$tokenIssuerSlug' does not match route issuer '$issuerSlug'"
            }
        }
        val identifier = data.credentialIdentifier
        val version = data.credentialVersion
        val metadata = metadataService.resolve(identifier, version)
        val trustSystem = data.trustSystem ?: request.trustSystem() ?: TrustSystem.Default
        val issuanceProfileId = requireNotNull(data.issuanceProfileId) {
            "Issuance profile is required"
        }
        if (metadata.supportedFormats == setOf(CredentialFormat.ZKP_VC)) {
            require(bbsCredentialIssuer.isAvailable(
                issuerSlug, identifier, version, trustSystem, issuanceProfileId,
            )) {
                "BBS credential issuance is not configured for '$identifier' version '$version'"
            }
        } else {
            signatureClient.validateKeyAccess(
                issuerSlug, trustSystem, identifier, version, issuanceProfileId,
            )
        }
        val encryptedTxCode = data.encryptedTxCode
        data.trustSystem?.let { tokenTrustSystem ->
            require(request.trustSystem() == null || tokenTrustSystem == request.trustSystem()) {
            "Process token trust system '$tokenTrustSystem' does not match requested trust system '${request.trustSystem()}'"
            }
        }
        if (request.token != null && enforcement.txCode == EnforcementMode.REQUIRED) {
            require(encryptedTxCode != null) { "The process token must contain a transaction code" }
        }
        require(properties.offerLifetimeSeconds > 0 && properties.refreshTokenLifetimeSeconds > 0
            && properties.accessTokenLifetimeSeconds > 0) { "Issuance lifetimes must be positive" }
        val snapshot = signingFlows.capture(
            issuerSlug,
            trustSystem,
            identifier,
            version,
            metadata.supportedFormats,
            credentialEncryption.policyFor(issuerSlug, issuanceProfileId),
            issuanceProfileId,
        )
        val now = Instant.now()
        val session = IssuanceSessionEntity().apply {
            signingSnapshot = snapshot.encode()
            offerExpiresAt = now.plusSeconds(properties.offerLifetimeSeconds)
            // A refresh at the end of its lifetime may still mint one full access token.
            signingExpiresAt = offerExpiresAt.plusSeconds(properties.refreshTokenLifetimeSeconds)
                .plusSeconds(properties.accessTokenLifetimeSeconds)
            connectionId = randomToken()
            this.issuerSlug = issuerSlug
            this.variant = variant
            this.trustSystem = trustSystem
            this.issuanceProfileId = issuanceProfileId
            credentialIdentifier = identifier
            credentialVersion = version
            preAuthorizedCode = randomToken()
            status = IssuanceStatus.OFFER_CREATED
        }
        sessions.save(session)
        signingFlows.retain(issuerSlug, session.id, snapshot, session.signingExpiresAt)
        sessionCrypto.writeSecrets(
            session,
            SessionSecrets(
                processToken = request.token,
                encryptedTxCode = encryptedTxCode.takeUnless { enforcement.txCode == EnforcementMode.DISABLED },
            ),
            sessionKey = null,
        )
        sessions.save(session)
        return offerResponse(session, metadata, OfferResponseMode.CREATED)
    }

    @Transactional(readOnly = true)
    fun credentialOffer(
        issuerSlug: String,
        variant: FlowVariant,
        identifier: String,
        version: String,
        connectionId: String,
    ): CredentialOfferResponse {
        val session = sessions.findByConnectionIdAndVariant(connectionId, variant)
            .filter {
                it.issuerSlug == issuerSlug &&
                    it.credentialIdentifier == identifier &&
                    it.credentialVersion == version &&
                    it.preAuthorizedCode != null
            }
            .orElseThrow { NoSuchElementException("Credential offer not found") }
        return offerResponse(
            session,
            metadataService.resolve(identifier, version),
            OfferResponseMode.FETCHED,
        )
    }

    private enum class OfferResponseMode { CREATED, FETCHED }

    private fun offerResponse(
        session: IssuanceSessionEntity,
        metadata: SchemaMetadataService.SchemaMetadata,
        mode: OfferResponseMode,
    ): CredentialOfferResponse {
        val encryptedTxCode = sessionCrypto.readSecrets(session).encryptedTxCode
        val transactionCode = if (
            encryptedTxCode != null && enforcement.txCode != EnforcementMode.DISABLED
        ) {
            CredentialOfferResponse.TransactionCode(
                "numeric",
                6,
                "Please provide the one-time code that was sent to you",
            )
        } else {
            null
        }
        val grants = CredentialOfferResponse.Grants(
            CredentialOfferResponse.PreAuthorizedCodeGrant(
                session.preAuthorizedCode!!,
                transactionCode,
            ),
        )
        return CredentialOfferResponse(
            issuerUrl(session),
            configurationIds(
                session.issuerSlug,
                session.credentialIdentifier,
                session.credentialVersion,
                metadata.supportedFormats,
                requireNotNull(session.issuanceProfileId) {
                    "Issuance profile is missing from the session"
                },
            ),
            grants,
            session.connectionId,
            offerUri(session).takeIf { mode == OfferResponseMode.CREATED },
        )
    }

    fun status(connectionId: String, variant: FlowVariant) =
        sessions.findByConnectionIdAndVariant(connectionId, variant)
            .orElseThrow { NoSuchElementException("Issuance connection not found") }

    @Transactional
    fun token(params: Map<String, String>, dpopProof: String?, httpMethod: String): TokenResponse {
        return token(params, dpopProof, httpMethod, null)
    }

    /**
     * Token endpoint entry point for an authorization extension. The optional binding is kept in
     * the generic issuance service so an extension can preserve RFC 9449's authorization-request
     * dpop_jkt binding while still reusing the normal pre-authorized session/token machinery.
     */
    @Transactional
    fun token(
        params: Map<String, String>,
        dpopProof: String?,
        httpMethod: String,
        expectedDpopJkt: String?,
    ): TokenResponse {
        val refreshGrant = params["grant_type"] == "refresh_token"
        val refreshToken = params["refresh_token"]
        val refreshEnvelope = if (refreshGrant) {
            sessionCrypto.decodeToken(
                refreshToken ?: throw IllegalArgumentException("refresh_token is required"),
                TokenType.REFRESH,
            )
        } else null
        val session = (if (refreshEnvelope != null) {
            sessions.findByRefreshTokenDigest(sessionCrypto.digest(refreshToken!!))
                .filter { it.id == refreshEnvelope.sessionId }
        } else when (params["grant_type"]) {
            PRE_AUTH_GRANT -> sessions.findByPreAuthorizedCode(params["pre-authorized_code"])
            else -> throw IllegalArgumentException("Unsupported grant_type")
        }).orElseThrow {
            if (refreshGrant) invalidRefreshGrant() else invalidPreAuthGrant()
        }
        val secrets = sessionCrypto.readSecrets(session, refreshEnvelope?.sessionKey)
        if (!refreshGrant) {
            if (session.offerExpiresAt?.isAfter(Instant.now()) != true) throw invalidPreAuthGrant()
            validateTransactionCode(params, secrets, session)
            session.preAuthorizedCode = null
        } else {
            if (session.refreshTokenExpiresAt?.isAfter(java.time.Instant.now()) != true) {
                throw invalidRefreshGrant()
            }
            session.refreshTokenUsageCount += 1
        }
        val issuanceData = secrets.processToken?.let(tokenDecoder::decode)
        val maxBatchSize = metadataService.resolve(
            session.credentialIdentifier,
            session.credentialVersion,
        ).maxBatchSize
        val deferredIssuance = issuanceData?.deferredTransactionId != null
        val credentialAlreadyIssued = session.status == IssuanceStatus.CREDENTIAL_ISSUED
        // A code issued for a 'dpop_jkt' may only be redeemed with that key (RFC 9449 section 10.1);
        // a refresh grant stays bound to the key the access token was issued to.
        val expectedJkt = if (refreshGrant) session.dpopJkt else expectedDpopJkt
        val dpopJkt = dpopService.verifyTokenEndpointProof(
            dpopProof,
            endpointUrl(session, "token"),
            httpMethod,
            expectedJkt = expectedJkt,
        )
        session.dpopJkt = dpopJkt
        val now = java.time.Instant.now()
        val accessExpiresAt = now.plusSeconds(properties.accessTokenLifetimeSeconds)
        val sessionKey = refreshEnvelope?.sessionKey ?: sessionCrypto.newSessionKey()
        val accessToken = sessionCrypto.issueToken(TokenType.ACCESS, session.id, sessionKey, accessExpiresAt)
        val shouldCreateRefreshToken = maxBatchSize > 1 || deferredIssuance
        val issuedRefreshToken = refreshToken?.takeIf { refreshGrant }
            ?: if (shouldCreateRefreshToken) {
                sessionCrypto.issueToken(
                    TokenType.REFRESH,
                    session.id,
                    sessionKey,
                    now.plusSeconds(properties.refreshTokenLifetimeSeconds),
                )
            } else null
        session.accessTokenDigest = sessionCrypto.digest(accessToken)
        session.accessTokenExpiresAt = accessExpiresAt
        if (issuedRefreshToken != null) {
            session.refreshTokenDigest = sessionCrypto.digest(issuedRefreshToken)
            session.refreshTokenExpiresAt = refreshEnvelope?.expiresAt
                ?: now.plusSeconds(properties.refreshTokenLifetimeSeconds)
        }
        sessionCrypto.writeSecrets(session, secrets, sessionKey)
        if (session.status != IssuanceStatus.CREDENTIAL_ISSUED) {
            session.status = IssuanceStatus.ACCESS_TOKEN_ISSUED
        }
        sessions.save(session)
        val returnRefreshToken = maxBatchSize > 1 || (deferredIssuance && !credentialAlreadyIssued)
        return TokenResponse(
            accessToken,
            if (dpopJkt == null) "Bearer" else "DPoP",
            properties.accessTokenLifetimeSeconds,
            randomToken(),
            300,
            issuedRefreshToken.takeIf { returnRefreshToken },
        )
    }

    private fun requestPolicy(session: IssuanceSessionEntity): CredentialEncryptionPolicy {
        val snapshot = IssuerFlowSnapshot.decode(session.signingSnapshot)
        val pinned = snapshot.encryption
        val profileId = requireNotNull(session.issuanceProfileId) {
            "Issuance profile is missing from the session"
        }
        val current = credentialEncryption.policyFor(session.issuerSlug, profileId)
        val retained = pinned.requestKeys.map { it.keyUri }.toSet()
        val added = current.requestKeys.filter { it.keyUri !in retained }
        if (added.isEmpty()) return pinned

        // A wallet may fetch newer metadata after receiving its offer. Retain those keys too.
        signingFlows.retain(session.issuerSlug, session.id,
            IssuerFlowSnapshot(encryption = pinned.copy(requestKeys = added)), session.signingExpiresAt)
        val merged = pinned.copy(requestKeys = pinned.requestKeys + added)
        session.signingSnapshot = snapshot.copy(encryption = merged).encode()
        sessions.save(session)
        return merged
    }

    private fun validateTransactionCode(
        params: Map<String, String>,
        secrets: SessionSecrets,
        session: IssuanceSessionEntity,
    ) {
        val supplied = params["tx_code"]
        val preAuthorized = params["grant_type"] == PRE_AUTH_GRANT
        if (!preAuthorized) {
            require(supplied == null) { "tx_code is only valid for the pre-authorized code flow" }
            return
        }
        if (enforcement.txCode == EnforcementMode.DISABLED) {
            require(supplied == null) { "Transaction codes are disabled" }
            return
        }
        val encrypted = secrets.encryptedTxCode
        if (encrypted == null) {
            require(supplied == null) { "No transaction code is expected for this grant" }
            return
        }
        if (supplied.isNullOrBlank()) {
            throw Oid4vciProtocolException("invalid_request", "tx_code is required")
        }
        runCatching {
            transactionCodes.verify(encrypted, session.credentialIdentifier, supplied)
        }.getOrElse {
            throw Oid4vciProtocolException("invalid_grant", "Invalid transaction code")
        }
    }

    @Transactional
    fun issue(
        accessToken: String,
        requestBody: String,
        authorizationScheme: String,
        dpopProof: String?,
        httpMethod: String,
        encryptedRequest: Boolean = false,
        userAgent: String? = null,
    ): CredentialEndpointResult {
        val authenticated = authenticateAccess(accessToken, authorizationScheme, dpopProof, httpMethod, "credential")
        val session = authenticated.session
        val secrets = authenticated.secrets
        // One resolve per request: the policy comes from the platform, and both the decryption of
        // this request and the encryption of its response have to use the same one.
        val policy = requestPolicy(session)
        val decodedRequest = decodeCredentialRequest(policy, requestBody, encryptedRequest, session.issuerSlug)
        val processToken = secrets.processToken ?: error("Authorization has not supplied credential data")
        val processData = tokenDecoder.decode(processToken)
        validateIssuerAndSchema(processData, session)
        val transactionId = processData.deferredTransactionId
        if (transactionId != null) {
            secrets.deferredCredentialData?.let { encodedData ->
                val data = tokenDecoder.decodeIssuanceData(encodedData, session.issuerSlug)
                validateIssuerAndSchema(data, session)
                return credentialEndpointResult(
                    policy,
                    200,
                    issueCredential(session, data, decodedRequest.body, userAgent),
                    decodedRequest.responseEncryption,
                )
            }
            return when (deferredCredentials.status(transactionId)) {
                DeferredCredentialStatus.PENDING -> {
                    secrets.credentialRequest = decodedRequest.body
                    sessionCrypto.writeSecrets(session, secrets, authenticated.envelope.sessionKey)
                    sessions.save(session)
                    deferredResponse(policy, transactionId, decodedRequest.responseEncryption)
                }
                DeferredCredentialStatus.READY -> {
                    val payload = deferredCredentials.credentialPayload(transactionId, session.issuerSlug)
                    val data = payload.credentialData
                    validateIssuerAndSchema(data, session)
                    secrets.deferredCredentialData = payload.encodedData
                    sessionCrypto.writeSecrets(session, secrets, authenticated.envelope.sessionKey)
                    val result = credentialEndpointResult(
                        policy,
                        200,
                        issueCredential(session, data, decodedRequest.body, userAgent),
                        decodedRequest.responseEncryption,
                    )
                    deferredCredentials.invalidate(transactionId)
                    result
                }
                DeferredCredentialStatus.ERROR,
                DeferredCredentialStatus.INVALIDATED,
                -> throw Oid4vciProtocolException("credential_request_denied", "Deferred credential cannot be issued")
            }
        }
        return credentialEndpointResult(
            policy,
            200,
            issueCredential(session, processData, decodedRequest.body, userAgent),
            decodedRequest.responseEncryption,
        )
    }

    private fun decodeCredentialRequest(
        policy: CredentialEncryptionPolicy,
        requestBody: String,
        encryptedRequest: Boolean,
        issuerSlug: String,
    ): DecodedCredentialRequest {
        credentialEncryption.validateRequestEncryption(policy, encryptedRequest)
        val body = if (encryptedRequest) credentialEncryption.decryptRequest(policy, requestBody, issuerSlug) else requestBody
        val request = runCatching { json.decodeFromString<CredentialRequest>(body) }
            .getOrElse { throw Oid4vciProtocolException("invalid_credential_request", "Malformed Credential Request", it) }
        credentialEncryption.validateResponseEncryption(
            policy, encryptedRequest, request.credentialResponseEncryption != null,
        )
        return DecodedCredentialRequest(body, request.credentialResponseEncryption?.parameters())
    }

    private fun decodeDeferredCredentialRequest(
        policy: CredentialEncryptionPolicy,
        requestBody: String,
        encryptedRequest: Boolean,
        issuerSlug: String,
    ): DecodedDeferredCredentialRequest {
        credentialEncryption.validateRequestEncryption(policy, encryptedRequest)
        val body = if (encryptedRequest) credentialEncryption.decryptRequest(policy, requestBody, issuerSlug) else requestBody
        val request = runCatching { json.decodeFromString<DeferredCredentialRequest>(body) }
            .getOrElse { throw Oid4vciProtocolException("invalid_transaction_id", "Invalid transaction_id", it) }
        credentialEncryption.validateResponseEncryption(
            policy, encryptedRequest, request.credentialResponseEncryption != null,
        )
        return DecodedDeferredCredentialRequest(request, request.credentialResponseEncryption?.parameters())
    }

    @Transactional
    fun deferredIssue(
        accessToken: String,
        requestBody: String,
        authorizationScheme: String,
        dpopProof: String?,
        httpMethod: String,
        encryptedRequest: Boolean = false,
        userAgent: String? = null,
    ): CredentialEndpointResult {
        val authenticated =
            authenticateAccess(accessToken, authorizationScheme, dpopProof, httpMethod, "deferred_credential")
        val session = authenticated.session
        val secrets = authenticated.secrets
        val policy = requestPolicy(session)
        val decodedRequest = decodeDeferredCredentialRequest(policy, requestBody, encryptedRequest, session.issuerSlug)
        val requestedTransactionId = runCatching {
            UUID.fromString(decodedRequest.request.transactionId)
        }.getOrElse { throw Oid4vciProtocolException("invalid_transaction_id", "Invalid transaction_id") }
        val processData = secrets.processToken?.let(tokenDecoder::decode)
            ?: throw Oid4vciProtocolException("invalid_transaction_id", "Session has no deferred transaction")
        if (processData.deferredTransactionId != requestedTransactionId ||
            session.status == IssuanceStatus.CREDENTIAL_ISSUED ||
            secrets.credentialRequest == null
        ) {
            throw Oid4vciProtocolException("invalid_transaction_id", "Unknown or consumed transaction_id")
        }
        val status = runCatching { deferredCredentials.status(requestedTransactionId) }
            .getOrElse {
                throw Oid4vciProtocolException("invalid_transaction_id", "Unknown transaction_id")
            }
        return when (status) {
            DeferredCredentialStatus.PENDING ->
                deferredResponse(policy, requestedTransactionId, decodedRequest.responseEncryption)
            DeferredCredentialStatus.READY -> {
                val payload = deferredCredentials.credentialPayload(requestedTransactionId, session.issuerSlug)
                val data = payload.credentialData
                validateIssuerAndSchema(data, session)
                secrets.deferredCredentialData = payload.encodedData
                sessionCrypto.writeSecrets(session, secrets, authenticated.envelope.sessionKey)
                val result = credentialEndpointResult(
                    policy,
                    200,
                    issueCredential(
                        session,
                        data,
                        secrets.credentialRequest!!,
                        userAgent,
                    ),
                    decodedRequest.responseEncryption,
                )
                deferredCredentials.invalidate(requestedTransactionId)
                result
            }
            DeferredCredentialStatus.ERROR,
            DeferredCredentialStatus.INVALIDATED,
            -> throw Oid4vciProtocolException("credential_request_denied", "Deferred credential cannot be issued")
        }
    }

    private fun authenticateAccess(
        accessToken: String,
        authorizationScheme: String,
        dpopProof: String?,
        httpMethod: String,
        endpoint: String,
    ): AuthenticatedSession {
        val accessEnvelope = sessionCrypto.decodeToken(accessToken, TokenType.ACCESS)
        val session = sessions.findByAccessTokenDigest(sessionCrypto.digest(accessToken))
            .filter { it.id == accessEnvelope.sessionId }
            .orElseThrow { IllegalArgumentException("Invalid access token") }
        require(session.accessTokenExpiresAt?.isAfter(java.time.Instant.now()) == true) { "Access token has expired" }
        if (session.dpopJkt != null) {
            require(authorizationScheme.equals("DPoP", ignoreCase = true)) {
                "A DPoP-bound access token must use the DPoP authorization scheme"
            }
            dpopService.verifyResourceProof(
                dpopProof,
                endpointUrl(session, endpoint),
                httpMethod,
                accessToken,
                session.dpopJkt,
            )
        } else {
            require(authorizationScheme.equals("Bearer", ignoreCase = true)) {
                "A Bearer access token must use the Bearer authorization scheme"
            }
            require(dpopProof.isNullOrBlank()) { "A DPoP proof cannot be used with an unbound Bearer token" }
        }
        return AuthenticatedSession(
            session,
            accessEnvelope,
            sessionCrypto.readSecrets(session, accessEnvelope.sessionKey),
        )
    }

    private fun validateIssuerAndSchema(data: CredentialData, session: IssuanceSessionEntity) {
        require(data.issuerSlug == null || data.issuerSlug == session.issuerSlug) {
            "Process token issuer '${data.issuerSlug}' does not match issuance issuer '${session.issuerSlug}'"
        }
        require(
            data.credentialIdentifier == session.credentialIdentifier &&
                data.credentialVersion == session.credentialVersion,
        ) { "Deferred credential data does not match the issuance session" }
    }

    private fun issueCredential(
        session: IssuanceSessionEntity,
        data: CredentialData,
        requestBody: String,
        userAgent: String? = null,
    ): CredentialResponse {
        val resolvedData = attributeCallbacks.resolve(data)
        val request = json.decodeFromString<CredentialRequest>(requestBody)
        val configurationId = request.credentialConfigurationId
        val format = request.format
            ?: when {
                configurationId?.endsWith("-mso-mdoc") == true -> "mso_mdoc"
                configurationId?.endsWith("-zkp-vc") == true -> "zkp_vc"
                configurationId?.endsWith("-w3c-vcdm") == true -> "vc+sd-jwt"
                else -> "dc+sd-jwt"
            }
        val metadata = metadataService.resolve(session.credentialIdentifier, session.credentialVersion)
        val schemaData = resolvedData.withSchemaMetadata(metadata)
        val holderProofs = extractAndVerifyHolderJwks(request, metadata.maxBatchSize)
        val trustSystem = session.trustSystem
            ?: org.heidiverse.heidi.issuer.model.TrustSystem.Default
        val requestedCredentialFormat = when (format) {
            "mso_mdoc" -> CredentialFormat.MSO_MDOC
            "dc+sd-jwt" -> CredentialFormat.SD_JWT
            "vc+sd-jwt" -> CredentialFormat.W3C_VCDM
            "zkp_vc" -> CredentialFormat.ZKP_VC
            else -> throw IllegalArgumentException("Unsupported credential format: $format")
        }
        require(requestedCredentialFormat in metadata.supportedFormats) {
            "Credential format '$format' is not registered for '${session.credentialIdentifier}' version '${session.credentialVersion}'"
        }
        val ocaUrl = ocaRenderUrl(metadata, properties.publicUrl, userAgent)
        val snapshot = IssuerFlowSnapshot.decode(session.signingSnapshot)
        val defaultIssuerClaim = issuerUrl(session)
        val credentialIssuerClaim = metadata.issuerClaimOverride
            ?: if (requestedCredentialFormat == CredentialFormat.ZKP_VC) {
                defaultIssuerClaim
            } else {
                snapshot.issuerClaim ?: defaultIssuerClaim
            }
        val statusList = if (format == "dc+sd-jwt" || format == "vc+sd-jwt") {
            signatureClient.allocateStatusList(
                session.issuerSlug,
                session.credentialIdentifier,
                session.credentialVersion,
                session.connectionId,
                holderProofs.jwks.size,
            )
        } else {
            null
        }
        require(statusList == null || statusList.indices.size == holderProofs.jwks.size) {
            "Status list allocation count does not match the credential batch"
        }
        val credentials = holderProofs.jwks.mapIndexed { index, holderJwk ->
            val statusReference = statusList?.let {
                CredentialData.StatusListReference(it.uri, it.indices[index])
            }
            when (format) {
                "mso_mdoc" -> credentialIssuer.issueMdoc(
                    schemaData,
                    session.issuerSlug,
                    metadata.docType,
                    metadata.namespace,
                    holderJwk,
                    snapshot,
                    trustSystem,
                )
                "dc+sd-jwt" -> credentialIssuer.issueSdJwt(
                    schemaData,
                    session.issuerSlug,
                    credentialIssuerClaim,
                    metadata.vct,
                    holderJwk,
                    snapshot,
                    trustSystem,
                    ocaUrl = ocaUrl,
                    statusList = statusReference,
                    vctMetadataUri = metadata.vctMetadataUri,
                )
                "vc+sd-jwt" -> credentialIssuer.issueW3c(
                    schemaData,
                    session.issuerSlug,
                    credentialIssuerClaim,
                    metadata.vct,
                    holderJwk,
                    snapshot,
                    trustSystem,
                    ocaUrl,
                    statusReference,
                )
                "zkp_vc" -> bbsCredentialIssuer.issue(
                    schemaData,
                    session.issuerSlug,
                    metadata.bbsCredentialType ?: metadata.vct,
                    holderJwk,
                    snapshot,
                    trustSystem,
                )
                else -> error("Credential format validation is inconsistent")
            }
        }
        session.status = IssuanceStatus.CREDENTIAL_ISSUED
        sessions.save(session)
        val nonce = randomToken()
        return if (holderProofs.batchRequest) {
            CredentialResponse.batch(nonce, credentials)
        } else {
            CredentialResponse.single(nonce, credentials.single())
        }
    }

    private fun deferredResponse(
        policy: CredentialEncryptionPolicy,
        transactionId: UUID,
        responseEncryption: CredentialResponseEncryptionParameters? = null,
    ) = credentialEndpointResult(
        policy,
        202,
        CredentialResponse.deferred(transactionId.toString(), properties.deferredIntervalSeconds),
        responseEncryption,
    )

    private fun credentialEndpointResult(
        policy: CredentialEncryptionPolicy,
        status: Int,
        body: CredentialResponse,
        responseEncryption: CredentialResponseEncryptionParameters? = null,
    ) = CredentialEndpointResult(
        status,
        body,
        responseEncryption?.let { credentialEncryption.encryptResponse(policy, body, it) },
    )

    fun credentialMetadata(
        issuerSlug: String,
        variant: FlowVariant,
        identifier: String,
        version: String,
        issuanceProfileId: String,
    ): IssuerMetadata {
        val base = "${properties.publicUrl.trimEnd('/')}/$issuerSlug/${variant.path()}/$identifier/$version"
        val metadata = metadataService.resolve(identifier, version)
        val encryptionPolicy = credentialEncryption.policyFor(issuerSlug, issuanceProfileId)
        val signingAlgorithms = if (metadata.supportedFormats.any(STANDARD_SIGNING_FORMATS::contains)) {
            signatureClient.configurationsFor(
                issuerSlug, identifier, version, issuanceProfileId,
            )
                .map { it.algorithm }.distinct()
                .also { algorithms ->
                    require(algorithms.isNotEmpty()) {
                        "No signing configuration exists for issuance profile '$issuanceProfileId'"
                    }
                }
        } else {
            emptyList()
        }
        val proofTypes = IssuerMetadata.ProofTypesSupported(
            IssuerMetadata.JwtProofType(listOf("ES256")),
        )
        fun withCredentialMetadata(
            configuration: IssuerMetadata.CredentialConfiguration,
        ): IssuerMetadata.CredentialConfiguration {
            val enriched = metadata.credentialDisplay?.let { display ->
                configuration.withCredentialMetadata(
                    IssuerMetadata.CredentialMetadata(listOf(display.toOid4vci())),
                )
            } ?: configuration
            val refreshDisabled = credentialRefreshDisabled(issuanceProfileId)
            return if (refreshDisabled == null) {
                enriched
            } else {
                enriched.withCredentialRefreshDisabled(refreshDisabled)
            }
        }
        val configurations = linkedMapOf<String, IssuerMetadata.CredentialConfiguration>()
        if (CredentialFormat.SD_JWT in metadata.supportedFormats) {
            configurations[sdJwtConfigurationId(identifier, version)] = withCredentialMetadata(
                IssuerMetadata.CredentialConfiguration(
                    "dc+sd-jwt",
                    metadata.vct,
                    null,
                    null,
                    listOf("jwk"),
                    signingAlgorithms,
                    proofTypes,
                    null,
                    null,
                    metadata.vctMetadataUri,
                ),
            )
        }
        if (CredentialFormat.MSO_MDOC in metadata.supportedFormats) {
            configurations[mdocConfigurationId(identifier, version)] = withCredentialMetadata(
                IssuerMetadata.CredentialConfiguration(
                    "mso_mdoc",
                    null,
                    metadata.docType,
                    null,
                    listOf("jwk"),
                    signingAlgorithms,
                    proofTypes,
                    null,
                    null,
                ),
            )
        }
        if (CredentialFormat.ZKP_VC in metadata.supportedFormats
            && bbsCredentialIssuer.isAvailable(
                issuerSlug, identifier, version, issuanceProfileId = issuanceProfileId,
            )) {
            configurations[bbsConfigurationId(identifier, version)] = withCredentialMetadata(
                IssuerMetadata.CredentialConfiguration(
                    "zkp_vc",
                    metadata.vct,
                    null,
                    null,
                    listOf("zk_attest", "linked_secret"),
                    listOf("bbs-termwise-signature-2023"),
                    proofTypes,
                    null,
                    null,
                ),
            )
        }
        if (CredentialFormat.W3C_VCDM in metadata.supportedFormats) {
            configurations[w3cConfigurationId(identifier, version)] = withCredentialMetadata(
                w3cCredentialConfiguration(metadata.vct, signingAlgorithms),
            )
        }
        val requestEncryption = credentialEncryption.requestEncryptionMetadata(encryptionPolicy)
        val responseEncryption = requestEncryption?.let {
            credentialEncryption.responseEncryptionMetadata(encryptionPolicy)
        }
        return IssuerMetadata(
            base,
            listOf(base),
            "$base/credential",
            "$base/deferred_credential",
            "$base/nonce",
            requestEncryption,
            responseEncryption,
            configurations,
            metadata.maxBatchSize.takeIf { it > 1 }
                ?.let { IssuerMetadata.BatchCredentialIssuance(it) },
            null,
            IssuanceProfilePolicy.metadataVersion(issuanceProfileId),
        )
    }

    fun signedCredentialMetadata(
        issuerSlug: String,
        variant: FlowVariant,
        identifier: String,
        version: String,
        trustSystem: org.heidiverse.heidi.issuer.model.TrustSystem,
        issuanceProfileId: String,
    ): String {
        var metadata = credentialMetadata(
            issuerSlug, variant, identifier, version, issuanceProfileId,
        )
        val effectiveTrustSystem = signatureClient.effectiveTrustSystem(
            issuerSlug, trustSystem, identifier, version, issuanceProfileId,
        )
        var swissDid: String? = null
        if (effectiveTrustSystem == org.heidiverse.heidi.issuer.model.TrustSystem.Switzerland) {
            val schema = metadataService.resolve(identifier, version)
            val swissTrust = signatureClient.swissTrustConfiguration(issuerSlug)
                ?: error("Swiss trust configuration is missing for issuer '$issuerSlug'")
            swissDid = swissTrust.did
            val identityStatement = swissTrust.identityStatement?.takeIf { it.isNotBlank() }
            val issuanceStatement = swissTrust.issuanceStatements[schema.vct]
            val configurations = issuanceStatement?.let { statement ->
                metadata.credentialConfigurationsSupported().mapValues { (_, configuration) ->
                    configuration.withProtectedIssuanceStatement(statement)
                }
            } ?: metadata.credentialConfigurationsSupported()
            metadata = metadata.withTrustStatements(identityStatement, configurations)
        }
        return signMetadata(
            issuerSlug,
            trustSystem,
            identifier,
            version,
            effectiveTrustSystem,
            metadata,
            metadata.credentialIssuer,
            swissDid,
            issuanceProfileId,
        )
    }

    fun signedAuthorizationMetadata(
        issuerSlug: String,
        identifier: String,
        version: String,
        metadata: AuthorizationServerMetadata,
        issuanceProfileId: String,
    ): String {
        val effectiveTrustSystem = signatureClient.effectiveTrustSystem(
            issuerSlug, TrustSystem.Default, identifier, version, issuanceProfileId,
        )
        val swissDid = if (effectiveTrustSystem == TrustSystem.Switzerland) {
            signatureClient.swissTrustConfiguration(issuerSlug)?.did
                ?: error("Swiss trust configuration is missing for issuer '$issuerSlug'")
        } else {
            null
        }
        return signMetadata(
            issuerSlug,
            TrustSystem.Default,
            identifier,
            version,
            effectiveTrustSystem,
            metadata,
            metadata.issuer,
            swissDid,
            issuanceProfileId,
        )
    }

    fun authorizationMetadata(
        issuerSlug: String,
        variant: FlowVariant,
        identifier: String,
        version: String,
    ): AuthorizationServerMetadata {
        val base = "${properties.publicUrl.trimEnd('/')}/$issuerSlug/${variant.path()}/$identifier/$version"
        return AuthorizationServerMetadata(
            base,
            "$base/token",
            listOf(PRE_AUTH_GRANT, "refresh_token"),
            listOf("none"),
            listOf(OPENID_CREDENTIAL_AUTHORIZATION_DETAIL),
            listOf("ES256").takeIf { dpopService.isEnabled() },
        )
    }

    private fun signMetadata(
        issuerSlug: String,
        trustSystem: TrustSystem,
        identifier: String,
        version: String,
        effectiveTrustSystem: TrustSystem,
        metadata: Any,
        subject: String,
        swissDid: String?,
        issuanceProfileId: String,
    ): String {
        val signer = signatureClient.forTrust(
            issuerSlug,
            trustSystem,
            identifier,
            version,
            issuanceProfileId,
        )
        if (effectiveTrustSystem == TrustSystem.EUDI) {
            require(signer.configuration.certificateChain.isNotEmpty()) {
                "EUDI signed issuer metadata requires the issuer signing certificate chain"
            }
        }
        val jwk = json.parseToJsonElement(signer.configuration.issuerJwk).jsonObject
        val kid = swissDid?.takeIf { it.isNotBlank() }
            ?.let { "$it#${signer.configuration.keyId}" }
            ?: jwk["kid"]?.jsonPrimitive?.contentOrNull
        val header = SignedMetadataHeader(
            SIGNED_METADATA_TYPE,
            signer.alg(),
            kid,
            signer.configuration.certificateChain.ifEmpty { null },
            IssuanceProfilePolicy.metadataVersion(issuanceProfileId),
        )
        val headerEncoded = base64Url(objectMapper.writeValueAsBytes(header))
        val payload = objectMapper.valueToTree<tools.jackson.databind.node.ObjectNode>(metadata)
        payload.put("sub", subject)
        payload.put("iat", Instant.now().epochSecond)
        val payloadEncoded = base64Url(objectMapper.writeValueAsBytes(payload))
        val signingInput = "$headerEncoded.$payloadEncoded"
        return "$signingInput.${base64Url(signer.sign(signingInput.encodeToByteArray()))}"
    }

    fun newDpopNonce(): String? = if (dpopService.isEnabled()) dpopService.issueNonce() else null

    fun jwtVcIssuerMetadata(
        issuerSlug: String,
        variant: FlowVariant,
        identifier: String,
        version: String,
    ): JwtVcIssuerMetadata {
        @Suppress("UNCHECKED_CAST")
        val keys = signatureClient.publicJwksFor(issuerSlug).mapNotNull { publicJwk ->
            runCatching {
                objectMapper.readValue(publicJwk, Map::class.java) as Map<String, Any>
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }.distinct()
        val issuer = "${properties.publicUrl.trimEnd('/')}/$issuerSlug/${variant.path()}/$identifier/$version"
        return JwtVcIssuerMetadata(issuer, JwtVcIssuerMetadata.JwkSet(keys))
    }

    private fun base64Url(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun extractAndVerifyHolderJwks(
        request: CredentialRequest,
        allowedBatchSize: Int,
    ): HolderProofs {
        request.jwk?.let { return HolderProofs(listOf(it.toString()), batchRequest = false) }
        val singleProof = request.proof?.jwt
        val batchProofs = request.proofs?.jwt
        require(singleProof == null || batchProofs == null) { "proof and proofs cannot both be set" }
        if (batchProofs != null) {
            require(batchProofs.isNotEmpty()) { "At least one JWT proof is required" }
            require(batchProofs.size <= allowedBatchSize) {
                "Requested batch size ${batchProofs.size} exceeds the credential schema maximum $allowedBatchSize"
            }
            return HolderProofs(batchProofs.map(::verifyHolderProof), batchRequest = true)
        }
        return HolderProofs(
            listOf(verifyHolderProof(singleProof ?: throw IllegalArgumentException("A JWT proof is required"))),
            batchRequest = false,
        )
    }

    private fun verifyHolderProof(proof: String): String {
        val jwt = Jwt(proof, JwtValidator(), System.currentTimeMillis())
        val jwk = runCatching {
            jwt.getHeader().jsonObject["jwk"]?.let { JWK.parse(it.toString()) }
        }.getOrNull() ?: throw IllegalArgumentException("Proof JWT has no jwk header")
        require(jwk.isPrivate.not()) { "Proof JWT must not contain a private key" }
        // EC only, as before: kapun alone would also accept RSA and OKP keys.
        jwk.toECKey()
        require(proof.verifyWith(jwk)) { "Invalid proof JWT signature" }
        return jwk.toJSONString()
    }

    private fun issuerUrl(session: IssuanceSessionEntity): String =
        "${properties.publicUrl.trimEnd('/')}/${session.issuerSlug}/${session.variant.path()}/${session.credentialIdentifier}/${session.credentialVersion}"

    private fun offerUri(session: IssuanceSessionEntity): String =
        "${issuerUrl(session)}/credential-offer/${session.connectionId}"

    /**
     * The publicly advertised URL of an endpoint, used as the expected DPoP 'htu'. It is derived
     * from the configured public URL rather than the request, because the request's host comes from
     * a client-supplied Host header and would let a proof minted for another origin be replayed here.
     */
    private fun endpointUrl(session: IssuanceSessionEntity, endpoint: String): String =
        "${issuerUrl(session)}/$endpoint"

    private fun configurationIds(
        issuerSlug: String,
        identifier: String,
        version: String,
        supportedFormats: Set<CredentialFormat>,
        issuanceProfileId: String,
    ) = buildList {
        if (CredentialFormat.SD_JWT in supportedFormats) add(sdJwtConfigurationId(identifier, version))
        if (CredentialFormat.MSO_MDOC in supportedFormats) add(mdocConfigurationId(identifier, version))
        if (CredentialFormat.ZKP_VC in supportedFormats
            && bbsCredentialIssuer.isAvailable(
                issuerSlug, identifier, version, issuanceProfileId = issuanceProfileId,
            )) {
            add(bbsConfigurationId(identifier, version))
        }
        if (CredentialFormat.W3C_VCDM in supportedFormats) add(w3cConfigurationId(identifier, version))
    }

    private fun sdJwtConfigurationId(identifier: String, version: String) = "$identifier-$version-sd-jwt"
    private fun mdocConfigurationId(identifier: String, version: String) = "$identifier-$version-mso-mdoc"
    private fun bbsConfigurationId(identifier: String, version: String) = "$identifier-$version-zkp-vc"
    private fun w3cConfigurationId(identifier: String, version: String) = "$identifier-$version-w3c-vcdm"

    private fun isSwissIssuanceProfile(profileId: String) =
        profileId == SWISS_ISSUANCE_PROFILE

    // Swiss renewal re-requests the credential endpoint with a valid access token.
    private fun credentialRefreshDisabled(profileId: String): Boolean? =
        if (isSwissIssuanceProfile(profileId)) false else null

    private fun randomToken(): String = ByteArray(24).also(SecureRandom()::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun invalidPreAuthGrant() = Oid4vciProtocolException(
        INVALID_GRANT,
        "The pre-authorized code is invalid, expired, or already used",
    )

    private fun invalidRefreshGrant() = Oid4vciProtocolException(
        INVALID_GRANT,
        "The refresh token is invalid or expired",
    )

    companion object {
        const val PRE_AUTH_GRANT = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
        private const val OPENID_CREDENTIAL_AUTHORIZATION_DETAIL = "openid_credential"
        private const val INVALID_GRANT = "invalid_grant"
        private const val SIGNED_METADATA_TYPE = "openidvci-issuer-metadata+jwt"
        private const val SWISS_ISSUANCE_PROFILE = "SWISS_ISSUANCE_2026_1"
        private val STANDARD_SIGNING_FORMATS = setOf(
            CredentialFormat.SD_JWT,
            CredentialFormat.MSO_MDOC,
            CredentialFormat.W3C_VCDM,
        )
    }
}

private data class HolderProofs(val jwks: List<String>, val batchRequest: Boolean)

private data class DecodedCredentialRequest(
    val body: String,
    val responseEncryption: CredentialResponseEncryptionParameters?,
)

private data class DecodedDeferredCredentialRequest(
    val request: DeferredCredentialRequest,
    val responseEncryption: CredentialResponseEncryptionParameters?,
)

@Serializable
private data class CredentialRequest(
    @SerialName("credential_configuration_id")
    val credentialConfigurationId: String? = null,
    val format: String? = null,
    val jwk: JsonElement? = null,
    val proof: JwtProof? = null,
    val proofs: JwtProofs? = null,
    @SerialName("credential_response_encryption")
    val credentialResponseEncryption: CredentialResponseEncryptionRequest? = null,
)

@Serializable
private data class JwtProof(val jwt: String? = null)

@Serializable
private data class JwtProofs(val jwt: List<String>? = null)

@Serializable
private data class DeferredCredentialRequest(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("credential_response_encryption")
    val credentialResponseEncryption: CredentialResponseEncryptionRequest? = null,
)

@Serializable
private data class CredentialResponseEncryptionRequest(
    val jwk: JsonObject,
    val enc: String,
    val zip: String? = null,
) {
    fun parameters() = CredentialResponseEncryptionParameters(jwk, enc, zip)
}

@JsonInclude(JsonInclude.Include.NON_NULL)
private data class SignedMetadataHeader(
    val typ: String,
    val alg: String,
    val kid: String?,
    val x5c: List<String>?,
    @JsonProperty("profile_version") val profileVersion: String?,
)

internal fun w3cCredentialConfiguration(
    credentialType: String,
    signingAlgorithm: String,
): IssuerMetadata.CredentialConfiguration =
    w3cCredentialConfiguration(credentialType, listOf(signingAlgorithm))

internal fun w3cCredentialConfiguration(
    credentialType: String,
    signingAlgorithms: List<String>,
) = IssuerMetadata.CredentialConfiguration(
    "vc+sd-jwt",
    null,
    null,
    IssuerMetadata.CredentialDefinition(listOf("VerifiableCredential", credentialType)),
    listOf("jwk"),
    signingAlgorithms,
    IssuerMetadata.ProofTypesSupported(IssuerMetadata.JwtProofType(listOf("ES256"))),
    null,
    null,
)

data class CredentialEndpointResult(
    val status: Int,
    val body: CredentialResponse,
    private val encryptedBody: String? = null,
) {
    val contentType: String
        get() = if (encryptedBody == null) "application/json" else "application/jwt"

    fun responseBody(): Any = encryptedBody ?: body
}

private data class AuthenticatedSession(
    val session: IssuanceSessionEntity,
    val envelope: TokenEnvelope,
    val secrets: SessionSecrets,
)

class Oid4vciProtocolException(
    val errorCode: String,
    override val message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
