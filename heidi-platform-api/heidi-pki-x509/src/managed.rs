// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

use crate::PkiError;
use kapun_x509::management as x509;

macro_rules! enums {
    ($($name:ident { $($variant:ident),* }),* $(,)?) => {$ (
        #[derive(Clone, Copy, uniffi::Enum)]
        pub enum $name { $($variant),* }
        impl From<$name> for x509::$name {
            fn from(value: $name) -> Self { match value { $($name::$variant => Self::$variant),* } }
        }
    )*};
}
enums! {
    SignatureAlgorithm { Es256, Es384, Es512, Rs256, Rs384, Rs512, Ps256, Ps384, Ps512, Ed25519, Ed448 },
    SignatureEncoding { JoseRaw, Certificate },
    CertificateRole { Authority, Leaf },
    NameOrder { Rfc4514, Input },
    ChainKind { Signing, Authorities },
    ChainEnd { Complete, Partial, Trusted },
    IssuerUsage { Required, IfPresent, Unchecked },
}

#[derive(uniffi::Record)]
pub struct NameAttribute {
    pub rdn: i64,
    pub oid: String,
    pub value: String,
    pub value_der: Vec<u8>,
}
impl From<x509::NameAttribute> for NameAttribute {
    fn from(v: x509::NameAttribute) -> Self {
        Self {
            rdn: i64::from(v.rdn),
            oid: v.oid,
            value: v.value,
            value_der: v.value_der,
        }
    }
}
#[derive(uniffi::Record)]
pub struct NameInfo {
    pub der: Vec<u8>,
    pub display: String,
    pub attributes: Vec<NameAttribute>,
}
impl From<x509::NameInfo> for NameInfo {
    fn from(v: x509::NameInfo) -> Self {
        Self {
            der: v.der,
            display: v.display,
            attributes: v.attributes.into_iter().map(Into::into).collect(),
        }
    }
}
#[derive(Clone, uniffi::Record)]
pub struct ExtensionInfo {
    pub oid: String,
    pub critical: bool,
    pub value_der: Vec<u8>,
}
impl From<x509::ExtensionInfo> for ExtensionInfo {
    fn from(v: x509::ExtensionInfo) -> Self {
        Self {
            oid: v.oid,
            critical: v.critical,
            value_der: v.value_der,
        }
    }
}
impl From<ExtensionInfo> for x509::ExtensionInfo {
    fn from(v: ExtensionInfo) -> Self {
        Self {
            oid: v.oid,
            critical: v.critical,
            value_der: v.value_der,
        }
    }
}
#[derive(uniffi::Record)]
pub struct CertificateInfo {
    pub der: Vec<u8>,
    pub fingerprint: String,
    pub serial: Vec<u8>,
    pub subject: NameInfo,
    pub issuer: NameInfo,
    pub spki_der: Vec<u8>,
    pub algorithm_der: Vec<u8>,
    pub not_before: i64,
    pub not_after: i64,
    pub is_ca: bool,
    pub path_length: Option<i64>,
    pub key_usage: Option<i32>,
    pub extended_key_usage: Vec<String>,
    pub dns_names: Vec<String>,
    pub uri_names: Vec<String>,
    pub ski: Option<Vec<u8>>,
    pub aki: Option<Vec<u8>>,
    pub extensions: Vec<ExtensionInfo>,
}
impl From<x509::CertificateInfo> for CertificateInfo {
    fn from(v: x509::CertificateInfo) -> Self {
        Self {
            der: v.der,
            fingerprint: v.fingerprint,
            serial: v.serial,
            subject: v.subject.into(),
            issuer: v.issuer.into(),
            spki_der: v.spki_der,
            algorithm_der: v.algorithm_der,
            not_before: v.not_before,
            not_after: v.not_after,
            is_ca: v.is_ca,
            path_length: v.path_length.map(i64::from),
            key_usage: v.key_usage.map(i32::from),
            extended_key_usage: v.extended_key_usage,
            dns_names: v.dns_names,
            uri_names: v.uri_names,
            ski: v.ski,
            aki: v.aki,
            extensions: v.extensions.into_iter().map(Into::into).collect(),
        }
    }
}
#[derive(uniffi::Record)]
pub struct CsrInfo {
    pub der: Vec<u8>,
    pub subject: NameInfo,
    pub spki_der: Vec<u8>,
    pub extensions: Vec<ExtensionInfo>,
}
impl From<x509::CsrInfo> for CsrInfo {
    fn from(v: x509::CsrInfo) -> Self {
        Self {
            der: v.der,
            subject: v.subject.into(),
            spki_der: v.spki_der,
            extensions: v.extensions.into_iter().map(Into::into).collect(),
        }
    }
}
#[derive(uniffi::Record)]
pub struct CertificateSpec {
    pub subject_der: Vec<u8>,
    pub issuer_certificate: Option<Vec<u8>>,
    pub spki_der: Vec<u8>,
    pub serial: Vec<u8>,
    pub not_before: i64,
    pub not_after: i64,
    pub algorithm: SignatureAlgorithm,
    pub role: CertificateRole,
    pub path_length: Option<i64>,
    pub key_usage: i32,
    pub dns_names: Vec<String>,
    pub uri_names: Vec<String>,
    pub ca_issuer_url: Option<String>,
    pub qc_types: Vec<String>,
    pub extensions: Vec<ExtensionInfo>,
}
#[derive(uniffi::Record)]
pub struct ChainPolicy {
    pub kind: ChainKind,
    pub end: ChainEnd,
    pub issuer_usage: IssuerUsage,
    pub time: i64,
    pub trusted_anchors: Vec<Vec<u8>>,
    pub expected_spki: Option<Vec<u8>>,
}

impl From<x509::Error> for PkiError {
    fn from(error: x509::Error) -> Self {
        Self::InvalidCertificate {
            reason: error.to_string(),
        }
    }
}

// Keep this boundary in the API while reusable operations stay in kapun-x509.
#[derive(uniffi::Object)]
pub struct PkiX509;
#[uniffi::export]
impl PkiX509 {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }
    pub fn inspect(&self, der: Vec<u8>) -> Result<CertificateInfo, PkiError> {
        Ok(x509::inspect_certificate(&der)?.into())
    }
    pub fn inspect_name(&self, der: Vec<u8>) -> Result<NameInfo, PkiError> {
        Ok(x509::inspect_name(&der)?.into())
    }
    pub fn encode_name(&self, name: String, order: NameOrder) -> Result<Vec<u8>, PkiError> {
        Ok(x509::encode_name(&name, order.into())?)
    }
    pub fn names_equal(&self, left: Vec<u8>, right: Vec<u8>) -> Result<bool, PkiError> {
        Ok(x509::names_equal(&left, &right)?)
    }
    pub fn matches_public_key(&self, der: Vec<u8>, spki: Vec<u8>) -> Result<bool, PkiError> {
        Ok(x509::matches_public_key(&der, &spki)?)
    }
    pub fn self_signed(&self, der: Vec<u8>) -> Result<bool, PkiError> {
        Ok(x509::is_self_signed(&der)?)
    }
    pub fn verify(&self, der: Vec<u8>, spki: Vec<u8>) -> Result<(), PkiError> {
        Ok(x509::verify_certificate(&der, &spki)?)
    }
    pub fn parse_pem_chain(&self, input: String) -> Result<Vec<Vec<u8>>, PkiError> {
        Ok(x509::parse_pem_chain(&input)?)
    }
    pub fn encode_pem_chain(&self, chain: Vec<Vec<u8>>) -> Result<String, PkiError> {
        Ok(x509::encode_pem_chain(&chain)?)
    }
    pub fn public_key_pem(&self, spki: Vec<u8>) -> Result<String, PkiError> {
        Ok(x509::public_key_pem(&spki)?)
    }
    pub fn validate_chain(
        &self,
        chain: Vec<Vec<u8>>,
        policy: ChainPolicy,
    ) -> Result<Vec<CertificateInfo>, PkiError> {
        let policy = x509::ChainPolicy {
            kind: policy.kind.into(),
            end: policy.end.into(),
            issuer_usage: policy.issuer_usage.into(),
            time: policy.time,
            trusted_anchors: policy.trusted_anchors,
            expected_spki: policy.expected_spki,
        };
        Ok(x509::validate_chain(&chain, &policy)?
            .into_iter()
            .map(Into::into)
            .collect())
    }
    pub fn validate_authority(
        &self,
        der: Vec<u8>,
        time: i64,
        usage: IssuerUsage,
    ) -> Result<CertificateInfo, PkiError> {
        Ok(x509::validate_authority(&der, time, usage.into())?.into())
    }
    pub fn check_validity(&self, der: Vec<u8>, time: i64) -> Result<(), PkiError> {
        Ok(x509::check_validity(
            &x509::inspect_certificate(&der)?,
            time,
        )?)
    }
    pub fn prepare(&self, spec: CertificateSpec) -> Result<Vec<u8>, PkiError> {
        let spec = x509::CertificateSpec {
            subject_der: spec.subject_der,
            issuer_certificate: spec.issuer_certificate,
            spki_der: spec.spki_der,
            serial: spec.serial,
            not_before: spec.not_before.try_into().map_err(crate::invalid_time)?,
            not_after: spec.not_after.try_into().map_err(crate::invalid_time)?,
            algorithm: spec.algorithm.into(),
            role: spec.role.into(),
            path_length: spec
                .path_length
                .map(u32::try_from)
                .transpose()
                .map_err(invalid_number)?,
            key_usage: spec.key_usage.try_into().map_err(invalid_number)?,
            dns_names: spec.dns_names,
            uri_names: spec.uri_names,
            ca_issuer_url: spec.ca_issuer_url,
            qc_types: spec.qc_types,
            extensions: spec.extensions.into_iter().map(Into::into).collect(),
        };
        Ok(x509::prepare_certificate_spec(&spec)?)
    }
    pub fn complete(
        &self,
        tbs: Vec<u8>,
        signature: Vec<u8>,
        encoding: SignatureEncoding,
        issuer_spki: Vec<u8>,
    ) -> Result<Vec<u8>, PkiError> {
        Ok(x509::complete_certificate_signed(
            &tbs,
            &signature,
            encoding.into(),
            &issuer_spki,
        )?)
    }
    pub fn prepare_csr(
        &self,
        subject: Vec<u8>,
        spki: Vec<u8>,
        dns: Vec<String>,
        uris: Vec<String>,
    ) -> Result<Vec<u8>, PkiError> {
        Ok(x509::prepare_csr(&subject, &spki, &dns, &uris)?)
    }
    pub fn complete_csr(
        &self,
        info: Vec<u8>,
        algorithm: SignatureAlgorithm,
        signature: Vec<u8>,
        encoding: SignatureEncoding,
    ) -> Result<Vec<u8>, PkiError> {
        Ok(x509::complete_csr(
            &info,
            algorithm.into(),
            &signature,
            encoding.into(),
        )?)
    }
    pub fn verify_csr(&self, der: Vec<u8>) -> Result<CsrInfo, PkiError> {
        Ok(x509::verify_csr(&der)?.into())
    }
    pub fn parse_pem_csr(&self, pem: String) -> Result<Vec<u8>, PkiError> {
        Ok(x509::parse_pem_csr(&pem)?)
    }
    pub fn encode_pem_csr(&self, der: Vec<u8>) -> Result<String, PkiError> {
        Ok(x509::encode_pem_csr(&der)?)
    }
}

fn invalid_number(_: std::num::TryFromIntError) -> PkiError {
    PkiError::InvalidCertificate {
        reason: "Invalid certificate constraint".into(),
    }
}
