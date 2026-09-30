// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

use std::sync::Arc;

use kapun_dcql_rust::verify::PossumExpression as KapunPossumExpression;

#[derive(uniffi::Object)]
pub struct PossumExpression {
	inner: Arc<KapunPossumExpression>,
}

#[uniffi::export]
impl PossumExpression {
    #[uniffi::constructor]
    pub fn from_str(expression: &str) -> Arc<Self> {
		Arc::new(Self {
			inner: KapunPossumExpression::from_str(expression),
		})
    }

    pub fn evaluate(self: &Arc<Self>, data: &str) -> Arc<Self> {
		Arc::new(Self {
			inner: self.inner.evaluate(data),
		})
    }

    pub fn is_truthy(&self) -> bool {
		self.inner.is_truthy()
    }

    pub fn is_null(&self) -> bool {
		self.inner.is_null()
    }

    pub fn as_json(&self) -> Option<String> {
		self.inner.as_json()
	}
}

uniffi::setup_scaffolding!();
