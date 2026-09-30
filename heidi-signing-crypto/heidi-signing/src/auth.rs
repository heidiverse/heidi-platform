// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

use std::fmt;

use ed25519_dalek::{
    DigestSigner, DigestVerifier, Signature, SigningKey, VerifyingKey,
    ed25519::SignatureEncoding,
    rand_core::{Rng, UnwrapErr},
};

use hmac::{
    KeyInit, Mac,
    digest::{FixedOutput, common::getrandom::SysRng},
};
use josekit::util::random_bytes;
use sha2::Sha256;

#[derive(uniffi::Error, Debug)]
pub enum AuthError {
    InvalidSeedLength,
    KeyDerivationFailed,
    InvalidPublicKey,
    WeakKey,
    InvalidSignature,
    SignatureVerificationFailed,
}
impl fmt::Display for AuthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AuthError::InvalidSeedLength => {
                f.write_str("Privatekey seed bytes need to be 32 bytes")
            }
            AuthError::KeyDerivationFailed => f.write_str("Failed to derive key"),
            AuthError::InvalidPublicKey => f.write_str("Publickey is invalid"),
            AuthError::WeakKey => f.write_str("Publickey is weak"),
            AuthError::InvalidSignature => f.write_str("Invalid Signature Encoding"),
            AuthError::SignatureVerificationFailed => f.write_str("Invalid Signature"),
        }
    }
}

#[uniffi::export]
pub fn generate_psk(provider: String) -> Result<Vec<u8>, AuthError> {
    let seed: Vec<u8> = random_bytes(32);
    let Ok(mut mac) = hmac::Hmac::<Sha256>::new_from_slice(seed.as_slice()) else {
        return Err(AuthError::InvalidSeedLength);
    };
    mac.update(b"PROVIDER");
    mac.update(provider.as_bytes());
    mac.update(b"PSK");
    Ok(mac.finalize_fixed().to_vec())
}

#[uniffi::export]
pub fn generate_seed() -> Vec<u8> {
    let mut rng = UnwrapErr(SysRng);
    let mut seed: [u8; 32] = [0; 32];
    rng.fill_bytes(&mut seed);
    seed.to_vec()
}

#[uniffi::export]
pub fn public_key(
    seed: Vec<u8>,
    pin: Option<Vec<u8>>,
    provider: String,
) -> Result<Vec<u8>, AuthError> {
    let Ok(mut mac) = hmac::Hmac::<Sha256>::new_from_slice(seed.as_slice()) else {
        return Err(AuthError::InvalidSeedLength);
    };
    mac.update(b"AUTH PROVIDER");
    mac.update(provider.as_bytes());
    if let Some(pin) = pin {
        mac.update(b"PIN");
        mac.update(&pin);
    }
    mac.update(b"FINISHED");

    let sk_bytes: [u8; 32] = mac.finalize_fixed().into();
    let sk = SigningKey::from_bytes(&sk_bytes);
    Ok(sk.verifying_key().as_bytes().to_vec())
}

#[uniffi::export]
pub fn authenticate(
    seed: Vec<u8>,
    pin: Option<Vec<u8>>,
    psk: Vec<u8>,
    provider: String,
    key_id: Option<String>,
    request: Vec<u8>,
) -> Result<Vec<u8>, AuthError> {
    let Ok(mut mac) = hmac::Hmac::<Sha256>::new_from_slice(seed.as_slice()) else {
        return Err(AuthError::InvalidSeedLength);
    };
    mac.update(b"AUTH PROVIDER");
    mac.update(provider.as_bytes());
    if let Some(pin) = pin {
        mac.update(b"PIN");
        mac.update(&pin);
    }
    mac.update(b"FINISHED");

    let sk_bytes: [u8; 32] = mac.finalize_fixed().into();
    let mut context = vec![];
    context.extend_from_slice(b"PASS");
    context.extend_from_slice(&psk);
    if let Some(key_id) = key_id {
        context.extend_from_slice(b"KEY ID");
        context.extend_from_slice(key_id.as_bytes());
    }
    context.extend_from_slice(b"END");
    let sk = SigningKey::from_bytes(&sk_bytes);
    let Ok(context) = sk.with_context(&context) else {
        return Err(AuthError::KeyDerivationFailed);
    };
    use ed25519_dalek::Digest;
    Ok(context
        .sign_digest(|d: &mut ed25519_dalek::Sha512| d.update(&request))
        .to_vec())
}

#[uniffi::export]
pub fn verify(
    pk: Vec<u8>,
    psk: Vec<u8>,
    key_id: Option<String>,
    payload: Vec<u8>,
    signature: Vec<u8>,
) -> Result<(), AuthError> {
    let Ok(pk) = VerifyingKey::try_from(pk.as_slice()) else {
        return Err(AuthError::InvalidPublicKey);
    };
    if pk.is_weak() {
        return Err(AuthError::WeakKey);
    }
    let mut context = vec![];
    context.extend_from_slice(b"PASS");
    context.extend_from_slice(&psk);
    if let Some(key_id) = key_id {
        context.extend_from_slice(b"KEY ID");
        context.extend_from_slice(key_id.as_bytes());
    }
    context.extend_from_slice(b"END");
    let Ok(context) = pk.with_context(&context) else {
        return Err(AuthError::InvalidPublicKey);
    };
    use ed25519_dalek::Digest;
    let Ok(signature) = Signature::from_slice(&signature) else {
        return Err(AuthError::InvalidSignature);
    };
    context
        .verify_digest(
            |d: &mut ed25519_dalek::Sha512| {
                d.update(&payload);
                Ok(())
            },
            &signature,
        )
        .map_err(|_| AuthError::SignatureVerificationFailed)
}

#[cfg(test)]
mod tests {
    use base64::{Engine, prelude::BASE64_STANDARD};

    use crate::auth::{AuthError, authenticate, generate_psk, generate_seed, public_key, verify};
    const PROVIDER: &str = "fancy-signing-provider";
    fn register_key(pin: Option<Vec<u8>>) -> (Vec<u8>, Vec<u8>, Vec<u8>) {
        // Client asks for PSK for a specific provider
        let provider = PROVIDER.to_string();
        let psk = generate_psk(provider.clone()).expect("Failed to generate PSK");
        // CLIENT <--PSK--- SERVER
        // Client generates seed
        let seed = generate_seed();
        let pk =
            public_key(seed.clone(), pin, provider.clone()).expect("Failed to derive public key");
        (seed, psk, pk)
    }

    #[test]
    fn register() {
        let psk = BASE64_STANDARD
            .decode("jddc2Pfjj48cgVwT6ISQWPiBVdHpIQqHz67otSqicG4=")
            .unwrap();
        let provider = "jwkSignerService".to_string();
        let seed = generate_seed();

        let pk =
            public_key(seed.clone(), None, provider.clone()).expect("Failed to derive public key");
        let sig = authenticate(seed.clone(), None, psk, provider, None, pk.clone()).unwrap();
        println!("seed: {}", BASE64_STANDARD.encode(&seed));
        println!("{}", BASE64_STANDARD.encode(&pk));
        println!("{}", BASE64_STANDARD.encode(&sig));
    }
    #[test]
    fn auth() {
        let psk = BASE64_STANDARD
            .decode("jddc2Pfjj48cgVwT6ISQWPiBVdHpIQqHz67otSqicG4=")
            .unwrap();
        let provider = "jwkSignerService".to_string();
        let seed = BASE64_STANDARD
            .decode("6I/C4J161TEW7UxfonebVPMMmggX3dGgfurxXaSAcAA=")
            .unwrap();

        let pk =
            public_key(seed.clone(), None, provider.clone()).expect("Failed to derive public key");
        let sig = authenticate(
            seed.clone(),
            None,
            psk,
            provider,
            Some("1234".to_string()),
            b"1786113072000:teststring".to_vec(),
        )
        .unwrap();
        println!("seed: {}", BASE64_STANDARD.encode(&seed));
        println!("{}", BASE64_STANDARD.encode(&pk));
        println!("{}", BASE64_STANDARD.encode(&sig));
    }

    #[test]
    fn test_registration_protocol() {
        // Client asks for PSK for a specific provider
        let provider = "fancy-signing-provider".to_string();
        let psk = generate_psk(provider.clone()).expect("Failed to generate PSK");
        // CLIENT <--PSK--- SERVER
        // Client generates seed
        let seed = generate_seed();
        // Client generates public key, specifying if the key is protected with a pin
        let pk = public_key(seed.clone(), Some(b"test_pin".to_vec()), provider.clone())
            .expect("Failed to derive public key");
        // Client authenticates public key with keyId None
        let sig = authenticate(
            seed.clone(),
            Some(b"test_pin".to_vec()),
            psk.clone(),
            provider.to_string(),
            None,
            pk.clone(),
        )
        .expect("Failed to authenticate");
        // Client sends public-key and sig to server
        // CLIENT ---(sig, pk)---> SERVER
        // Server verifies signature (and registers key for provider)
        assert!(verify(pk.clone(), psk.clone(), None, pk.clone(), sig).is_ok())
    }

    #[test]
    fn invalid_pin_fails() {
        let (seed, psk, pk) = register_key(Some(b"test_pin".to_vec()));
        let sig = authenticate(
            seed.clone(),
            Some(b"invalid-pin".to_vec()),
            psk.clone(),
            PROVIDER.to_string(),
            Some("default".to_string()),
            vec![1, 2, 3, 4],
        )
        .expect("failed to authenticate");
        assert!(matches!(
            verify(
                pk.clone(),
                psk.clone(),
                Some("default".to_string()),
                vec![1, 2, 3, 4],
                sig,
            ),
            Err(AuthError::SignatureVerificationFailed)
        ));

        let sig = authenticate(
            seed,
            Some(b"test_pin".to_vec()),
            psk.clone(),
            PROVIDER.to_string(),
            Some("default".to_string()),
            vec![1, 2, 3, 4],
        )
        .expect("failed to authenticate");

        assert!(
            verify(
                pk.clone(),
                psk.clone(),
                Some("default".to_string()),
                vec![1, 2, 3, 4],
                sig,
            )
            .is_ok()
        )
    }

    #[test]
    fn no_pin() {
        let (seed, psk, pk) = register_key(None);
        let sig = authenticate(
            seed.clone(),
            None,
            psk.clone(),
            PROVIDER.to_string(),
            Some("default".to_string()),
            vec![1, 2, 3, 4],
        )
        .expect("failed to authenticate");
        assert!(
            verify(
                pk.clone(),
                psk.clone(),
                Some("default".to_string()),
                vec![1, 2, 3, 4],
                sig,
            )
            .is_ok()
        );
        let sig = authenticate(
            seed.clone(),
            Some(b"test_pin".to_vec()),
            psk.clone(),
            PROVIDER.to_string(),
            Some("default".to_string()),
            vec![1, 2, 3, 4],
        )
        .expect("failed to authenticate");
        assert!(matches!(
            verify(
                pk.clone(),
                psk.clone(),
                Some("default".to_string()),
                vec![1, 2, 3, 4],
                sig,
            ),
            Err(AuthError::SignatureVerificationFailed)
        ));
    }
}
