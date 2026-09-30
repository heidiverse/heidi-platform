// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

pub mod auth;
pub mod store;

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use base64::{Engine, prelude::BASE64_STANDARD};
    use josekit::jws::alg::ecdsa::EcdsaJwsAlgorithm::Es256;

    use crate::store::{JoseKeyPair, KeyError, Store, StoreObject, public_jwk_from_der};

    #[test]
    fn testkey() {
        let k = Es256.generate_key_pair().unwrap();
        let sk = BASE64_STANDARD.encode(k.to_der_private_key());
        let pk = BASE64_STANDARD.encode(k.to_der_public_key());
        println!("{}/{}", sk, pk);
        struct TestStore(String, String);
        impl Store for TestStore {
            fn get_secret_key(self: Arc<Self>, _alias: String) -> Option<Vec<u8>> {
                Some(BASE64_STANDARD.decode(self.0.clone()).unwrap())
            }

            fn get_certificate(self: Arc<Self>, _alias: String) -> Option<Vec<u8>> {
                Some(BASE64_STANDARD.decode(self.1.clone()).unwrap())
            }

            fn get_key_algorithm(self: Arc<Self>, _alias: String) -> Option<String> {
                Some("ES256".to_string())
            }
        }
        let o = StoreObject::new(Arc::new(TestStore(sk, pk)));
        let signature = o.sign("", b"test".to_vec()).unwrap();
        o.verify("", signature, b"test".to_vec()).unwrap();
    }

    #[test]
    fn generates_supported_algorithms() {
        for algorithm in [
            "ES256",
            "ES384",
            "ES512",
            "EdDSA",
            "PS256",
            "PS384",
            "PS512",
            "RS256",
            "RS384",
            "RS512",
            "ML-DSA-44",
            "ML-DSA-65",
            "ML-DSA-87",
        ] {
            let generated = JoseKeyPair::generate(algorithm.to_string()).unwrap();
            let imported = JoseKeyPair::new(generated.key_bytes(), algorithm.to_string()).unwrap();
            assert_eq!(generated.public_key(), imported.public_key());

            let public_jwk = public_jwk_from_der(
                generated.public_key(),
                algorithm.to_string(),
                "test-key".to_string(),
            )
            .unwrap_or_else(|error| panic!("{algorithm}: {error:?}"));
            assert!(public_jwk.contains("\"kid\":\"test-key\""));
            assert!(public_jwk.contains(&format!("\"alg\":\"{algorithm}\"")));
            assert!(!public_jwk.contains("\"d\":"));
            if algorithm == "EdDSA" {
                assert!(public_jwk.contains("\"crv\":\"Ed25519\""));
            }
        }
    }

    #[test]
    fn rejects_unsupported_algorithm() {
        assert!(matches!(
            JoseKeyPair::generate("HS256".to_string()),
            Err(KeyError::UnsupportedAlgorithm)
        ));
    }
}
