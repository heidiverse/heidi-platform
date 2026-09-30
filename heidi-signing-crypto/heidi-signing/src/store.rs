// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

use josekit::jwk::alg::ed::EdCurve;
use josekit::jwk::{Jwk, KeyPair, PublicKey as PublicKeyTrait};
use josekit::jws::alg::ecdsa::EcdsaJwsAlgorithm::{Es256, Es384, Es512};
use josekit::jws::alg::eddsa::EddsaJwsAlgorithm::Eddsa;
use josekit::jws::alg::ml_dsa::MldsaJwsAlgorithm::{MlDSA44, MlDSA65, MlDSA87};
use josekit::jws::alg::rsassa::RsassaJwsAlgorithm::{Rs256, Rs384, Rs512};
use josekit::jws::alg::rsassa_pss::RsassaPssJwsAlgorithm::{Ps256, Ps384, Ps512};
use josekit::jws::{JwsSigner, JwsVerifier};
use std::fmt::{Display, Formatter};
use std::sync::Arc;

#[derive(uniffi::Object)]
pub struct StoreObject {
    store: Arc<dyn Store>,
}

#[uniffi::export(with_foreign)]
pub trait Store: Send + Sync {
    fn get_secret_key(self: Arc<Self>, alias: String) -> Option<Vec<u8>>;
    fn get_certificate(self: Arc<Self>, alias: String) -> Option<Vec<u8>>;
    fn get_key_algorithm(self: Arc<Self>, alias: String) -> Option<String>;
}

#[derive(uniffi::Error, Debug)]
pub enum SigningError {
    Failed,
}
impl Display for SigningError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            SigningError::Failed => f.write_str("Signing Failed"),
        }
    }
}

#[derive(uniffi::Error, Debug)]
pub enum VerifyError {
    KeyNotFound,
    InvalidKeyFormat,
    Failed,
}
impl Display for VerifyError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            VerifyError::Failed => f.write_str("Verify Failed"),
            VerifyError::KeyNotFound => f.write_str("Key Not Found"),
            VerifyError::InvalidKeyFormat => f.write_str("Invalid Key Format"),
        }
    }
}

#[derive(uniffi::Error, Debug)]
pub enum KeyError {
    InvalidKeyFormat,
    UnsupportedAlgorithm,
    GenerationFailed,
}
impl Display for KeyError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            KeyError::InvalidKeyFormat => f.write_str("Invalid Key Format"),
            KeyError::UnsupportedAlgorithm => f.write_str("Unsupported Key Algorithm"),
            KeyError::GenerationFailed => f.write_str("Key Generation Failed"),
        }
    }
}

#[derive(uniffi::Object)]
pub struct JoseKeyPair {
    kp: Box<dyn KeyPair>,
}
#[uniffi::export]
impl JoseKeyPair {
    #[uniffi::constructor]
    pub fn new(private_key: Vec<u8>, algorithm: String) -> Result<Self, KeyError> {
        Ok(Self {
            kp: key_pair_from_der(&algorithm, &private_key)?,
        })
    }
    #[uniffi::constructor]
    pub fn generate(algorithm: String) -> Result<Self, KeyError> {
        let key_pair: Box<dyn KeyPair> = match algorithm.as_str() {
            "ES256" => Box::new(
                Es256
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "ES384" => Box::new(
                Es384
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "ES512" => Box::new(
                Es512
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "EdDSA" => Box::new(
                Eddsa
                    .generate_key_pair(EdCurve::Ed25519)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "PS256" => Box::new(
                Ps256
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "PS384" => Box::new(
                Ps384
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "PS512" => Box::new(
                Ps512
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "RS256" => Box::new(
                Rs256
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "RS384" => Box::new(
                Rs384
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "RS512" => Box::new(
                Rs512
                    .generate_key_pair(3072)
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "ML-DSA-44" => Box::new(
                MlDSA44
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "ML-DSA-65" => Box::new(
                MlDSA65
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            "ML-DSA-87" => Box::new(
                MlDSA87
                    .generate_key_pair()
                    .map_err(|_| KeyError::GenerationFailed)?,
            ),
            _ => return Err(KeyError::UnsupportedAlgorithm),
        };
        Ok(Self { kp: key_pair })
    }
    pub fn public_key(&self) -> Vec<u8> {
        self.kp.to_der_public_key()
    }
    pub fn key_bytes(&self) -> Vec<u8> {
        self.kp.to_der_private_key()
    }
    pub fn to_public_jwk(&self) -> String {
        self.kp.to_jwk_public_key().to_string()
    }
}

/// Converts a DER SubjectPublicKeyInfo returned by a signing service into a public JWK.
///
/// Keeping this conversion here ensures that key validation and JOSE serialization use the
/// same implementation as local software signing.
#[uniffi::export]
pub fn public_jwk_from_der(
    public_key: Vec<u8>,
    algorithm: String,
    key_id: String,
) -> Result<String, KeyError> {
    let mut jwk = match algorithm.as_str() {
        "ES256" => verifier_jwk(Es256.verifier_from_der(&public_key))?,
        "ES384" => verifier_jwk(Es384.verifier_from_der(&public_key))?,
        "ES512" => verifier_jwk(Es512.verifier_from_der(&public_key))?,
        "EdDSA" => verifier_jwk(Eddsa.verifier_from_der(&public_key))?,
        "PS256" => verifier_jwk(Ps256.verifier_from_der(&public_key))?,
        "PS384" => verifier_jwk(Ps384.verifier_from_der(&public_key))?,
        "PS512" => verifier_jwk(Ps512.verifier_from_der(&public_key))?,
        "RS256" => verifier_jwk(Rs256.verifier_from_der(&public_key))?,
        "RS384" => verifier_jwk(Rs384.verifier_from_der(&public_key))?,
        "RS512" => verifier_jwk(Rs512.verifier_from_der(&public_key))?,
        "ML-DSA-44" => verifier_jwk(MlDSA44.verifier_from_der(&public_key))?,
        "ML-DSA-65" => verifier_jwk(MlDSA65.verifier_from_der(&public_key))?,
        "ML-DSA-87" => verifier_jwk(MlDSA87.verifier_from_der(&public_key))?,
        _ => return Err(KeyError::UnsupportedAlgorithm),
    };
    jwk.set_algorithm(&algorithm);
    jwk.set_key_id(key_id);
    jwk.set_key_use("sig");
    Ok(jwk.to_string())
}

fn verifier_jwk<T: PublicKeyTrait>(
    verifier: Result<T, josekit::JoseError>,
) -> Result<Jwk, KeyError> {
    verifier
        .map(|verifier| verifier.to_jwk_public_key())
        .map_err(|_| KeyError::InvalidKeyFormat)
}

#[uniffi::export]
impl StoreObject {
    #[uniffi::constructor]
    pub fn new(store: Arc<dyn Store>) -> Self {
        Self { store }
    }
    pub fn sign(&self, alias: &str, payload: Vec<u8>) -> Result<Vec<u8>, SigningError> {
        let algorithm = self
            .store
            .clone()
            .get_key_algorithm(alias.to_string())
            .ok_or(SigningError::Failed)?;
        let entry = self
            .store
            .clone()
            .get_secret_key(alias.to_string())
            .ok_or(SigningError::Failed)?;
        let signer = signer_from_der(&algorithm, &entry).map_err(|_| SigningError::Failed)?;
        signer.sign(&payload).map_err(|_| SigningError::Failed)
    }
    pub fn sign_digest(&self, alias: &str, prehashed: Vec<u8>) -> Result<Vec<u8>, SigningError> {
        let algorithm = self
            .store
            .clone()
            .get_key_algorithm(alias.to_string())
            .ok_or(SigningError::Failed)?;
        let entry = self
            .store
            .clone()
            .get_secret_key(alias.to_string())
            .ok_or(SigningError::Failed)?;
        let signer = signer_from_der(&algorithm, &entry).map_err(|_| SigningError::Failed)?;
        signer
            .sign_prehashed(&prehashed)
            .map_err(|_| SigningError::Failed)
    }
    pub fn verify(
        &self,
        alias: &str,
        signature: Vec<u8>,
        payload: Vec<u8>,
    ) -> Result<(), VerifyError> {
        let algorithm = self
            .store
            .clone()
            .get_key_algorithm(alias.to_string())
            .ok_or(VerifyError::KeyNotFound)?;
        let entry = self
            .store
            .clone()
            .get_certificate(alias.to_string())
            .ok_or(VerifyError::Failed)?;
        let cert =
            match kapun_x509::extract_public_key(&entry).map_err(|_| VerifyError::KeyNotFound) {
                Ok(e) => e,
                Err(_) => entry,
            };
        let verifier =
            verifier_from_der(&algorithm, &cert).map_err(|_| VerifyError::InvalidKeyFormat)?;
        verifier
            .verify(&payload, &signature)
            .map_err(|_| VerifyError::Failed)
    }
    pub fn verify_prehashed(
        &self,
        alias: &str,
        signature: Vec<u8>,
        payload: Vec<u8>,
    ) -> Result<(), VerifyError> {
        let algorithm = self
            .store
            .clone()
            .get_key_algorithm(alias.to_string())
            .ok_or(VerifyError::KeyNotFound)?;
        let entry = self
            .store
            .clone()
            .get_certificate(alias.to_string())
            .ok_or(VerifyError::Failed)?;
        let cert =
            match kapun_x509::extract_public_key(&entry).map_err(|_| VerifyError::KeyNotFound) {
                Ok(e) => e,
                Err(_) => entry,
            };
        let verifier =
            verifier_from_der(&algorithm, &cert).map_err(|_| VerifyError::InvalidKeyFormat)?;
        verifier
            .verify_prehashed(&payload, &signature)
            .map_err(|_| VerifyError::Failed)
    }
}

fn key_pair_from_der(algorithm: &str, private_key: &[u8]) -> Result<Box<dyn KeyPair>, KeyError> {
    let key_pair = match algorithm {
        "ES256" => Es256
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "ES384" => Es384
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "ES512" => Es512
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "EdDSA" => Eddsa
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "PS256" => Ps256
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "PS384" => Ps384
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "PS512" => Ps512
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "RS256" => Rs256
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "RS384" => Rs384
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "RS512" => Rs512
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "ML-DSA-44" => MlDSA44
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "ML-DSA-65" => MlDSA65
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        "ML-DSA-87" => MlDSA87
            .key_pair_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn KeyPair>),
        _ => return Err(KeyError::UnsupportedAlgorithm),
    }
    .map_err(|_| KeyError::InvalidKeyFormat)?;
    Ok(key_pair)
}

fn signer_from_der(algorithm: &str, private_key: &[u8]) -> Result<Box<dyn JwsSigner>, KeyError> {
    let signer = match algorithm {
        "ES256" => Es256
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "ES384" => Es384
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "ES512" => Es512
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "EdDSA" => Eddsa
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "PS256" => Ps256
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "PS384" => Ps384
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "PS512" => Ps512
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "RS256" => Rs256
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "RS384" => Rs384
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "RS512" => Rs512
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "ML-DSA-44" => MlDSA44
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "ML-DSA-65" => MlDSA65
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        "ML-DSA-87" => MlDSA87
            .signer_from_der(private_key)
            .map(|key| Box::new(key) as Box<dyn JwsSigner>),
        _ => return Err(KeyError::UnsupportedAlgorithm),
    }
    .map_err(|_| KeyError::InvalidKeyFormat)?;
    Ok(signer)
}

fn verifier_from_der(algorithm: &str, public_key: &[u8]) -> Result<Box<dyn JwsVerifier>, KeyError> {
    let verifier = match algorithm {
        "ES256" => Es256
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "ES384" => Es384
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "ES512" => Es512
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "EdDSA" => Eddsa
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "PS256" => Ps256
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "PS384" => Ps384
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "PS512" => Ps512
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "RS256" => Rs256
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "RS384" => Rs384
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "RS512" => Rs512
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "ML-DSA-44" => MlDSA44
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "ML-DSA-65" => MlDSA65
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        "ML-DSA-87" => MlDSA87
            .verifier_from_der(public_key)
            .map(|key| Box::new(key) as Box<dyn JwsVerifier>),
        _ => return Err(KeyError::UnsupportedAlgorithm),
    }
    .map_err(|_| KeyError::InvalidKeyFormat)?;
    Ok(verifier)
}
