// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

/** One persisted reference that cannot yet be represented by an identity slot. */
public record IdentityKeyViolation(String code, String subject, String detail) {}
