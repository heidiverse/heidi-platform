// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

/**
 * Provider contract for signing keys.
 *
 * <p>One interface between everything that needs a signature and the backends that hold keys:
 * software keys in a database column, HashiCorp Vault, a PKCS#11 token, a cloud KMS, a qualified
 * signature service. Keys are addressed by URI, so a single stored value routes an operation and
 * stays portable between environments.
 *
 * <p>Deliberately narrow. It offers raw signing rather than handing back a
 * {@link java.security.PrivateKey}, which would mean implementing a JCA provider with a
 * {@code SignatureSpi} for every algorithm we advertise. Libraries that want their own signing type
 * get a small adapter instead — BouncyCastle's {@code ContentSigner} is three methods, and Nimbus's
 * {@code JWSSigner} is comparable — and those adapters live with the code that needs them rather
 * than here, so this package keeps its dependencies at zero.
 *
 * <p>Adding a JCA provider later stays possible and would build on this same {@code sign} call,
 * without changing anything defined here.
 *
 * <p>A provider that reaches its keys over the network speaks the Heidi Signing Protocol, specified
 * under {@code spec/signing-protocol/} in this repository. That protocol, not this interface, is the
 * seam third parties implement against: it binds no one to our language or release cycle.
 */
package org.heidiverse.heidi.shared.signing;
