// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

mod managed;

// Platform-local UniFFI facade; certificate construction lives in kapun-x509.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum PkiError {
    #[error("Invalid certificate: {reason}")]
    InvalidCertificate { reason: String },
}

#[derive(uniffi::Record)]
pub struct CertificateRequest {
    pub issuer_der: Vec<u8>,
    pub subject_der: Vec<u8>,
    pub spki_der: Vec<u8>,
    pub serial: Vec<u8>,
    pub not_before: i64,
    pub not_after: i64,
    pub algorithm_der: Vec<u8>,
    pub extensions_der: Vec<u8>,
}

#[uniffi::export]
pub fn prepare_certificate(request: CertificateRequest) -> Result<Vec<u8>, PkiError> {
    kapun_x509::builder::prepare_certificate(
        &request.issuer_der,
        &request.subject_der,
        &request.spki_der,
        &request.serial,
        request.not_before.try_into().map_err(invalid_time)?,
        request.not_after.try_into().map_err(invalid_time)?,
        &request.algorithm_der,
        &request.extensions_der,
    )
    .map_err(|error| PkiError::InvalidCertificate {
        reason: error.to_string(),
    })
}

#[uniffi::export]
pub fn complete_certificate(tbs: Vec<u8>, signature: Vec<u8>) -> Result<Vec<u8>, PkiError> {
    kapun_x509::builder::complete_certificate(&tbs, &signature).map_err(|error| {
        PkiError::InvalidCertificate {
            reason: error.to_string(),
        }
    })
}

uniffi::setup_scaffolding!();

fn invalid_time(_: std::num::TryFromIntError) -> PkiError {
    PkiError::InvalidCertificate {
        reason: "Invalid validity timestamp".into(),
    }
}

#[derive(uniffi::Object)]
pub struct PkiCertificate {
    tbs: Vec<u8>,
}

#[uniffi::export]
impl PkiCertificate {
    #[uniffi::constructor]
    pub fn new(request: CertificateRequest) -> Result<Self, PkiError> {
        Ok(Self {
            tbs: prepare_certificate(request)?,
        })
    }
    pub fn tbs(&self) -> Vec<u8> {
        self.tbs.clone()
    }
    pub fn complete(&self, signature: Vec<u8>) -> Result<Vec<u8>, PkiError> {
        complete_certificate(self.tbs.clone(), signature)
    }
}
